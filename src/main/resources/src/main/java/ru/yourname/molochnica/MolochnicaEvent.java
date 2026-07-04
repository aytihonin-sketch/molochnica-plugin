package ru.yourname.molochnica;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MolochnicaEvent extends JavaPlugin implements Listener, CommandExecutor {

    private static Economy econ = null;
    private Cow bossInstance = null;
    private final Map<UUID, Double> damageMap = new HashMap<>();

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault или плагин на экономику не найден! Выдача 50к работать не будет.");
        }
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("startboss").setExecutor(this);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player p = (Player) sender;
        
        // Запуск ивента на месте, где стоит админ
        startBossEvent(p.getLocation());
        p.sendMessage("§aИвент запущен!");
        return true;
    }

    private void startBossEvent(Location loc) {
        // ТАЙМЕР 30 СЕКУНД
        Bukkit.broadcastMessage("§e[PvP Арена] §fБосс появится через §c30 сек!");

        new BukkitRunnable() {
            @Override
            public void run() {
                // ТАЙМЕР 15 СЕКУНД
                Bukkit.broadcastMessage("§e[PvP Арена] §fБосс появится через §c15 сек!");
            }
        }.runTaskLater(this, 15 * 20L);

        new BukkitRunnable() {
            @Override
            public void run() {
                // СПАВН БОССА
                Bukkit.broadcastMessage("§e[PvP Арена] §c§lПоявился босс на PvP арене!");
                damageMap.clear();
                
                bossInstance = (Cow) loc.getWorld().spawnEntity(loc, EntityType.COW);
                bossInstance.setCustomName("§f§lМолочница");
                bossInstance.setCustomNameVisible(true);
                
                // Настройка ХП босса (например, 500 ХП)
                bossInstance.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(500.0);
                bossInstance.setHealth(500.0);

                startBossAI();
            }
        }.runTaskLater(this, 30 * 20L);
    }

    private void startBossAI() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (bossInstance == null || bossInstance.isDead()) {
                    cancel();
                    return;
                }

                // Удар 1: Бьет по земле (Каждые 7 секунд)
                bossInstance.getWorld().spawnParticle(Particle.CLOUD, bossInstance.getLocation(), 40, 2, 0.1, 2, 0.1);
                for (Entity entity : bossInstance.getNearbyEntities(5, 3, 5)) {
                    if (entity instanceof Player) {
                        Player p = (Player) entity;
                        dealCustomDamage(p, 3.0); // 3 ХП без брони
                        p.setVelocity(new Vector(0, 0.4, 0)); // Подкидывание
                    }
                }

                // Удар 2: Выстрел пеной в топ-дамагера (Каждые 7 секунд, со смещением)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (bossInstance == null || bossInstance.isDead()) return;
                        Player target = getTopDamager();
                        if (target != null && target.getLocation().distance(bossInstance.getLocation()) < 30) {
                            Snowball foam = bossInstance.launchProjectile(Snowball.class);
                            foam.setCustomName("foam_projectile");
                            // Визуальный след снежинок за снарядом
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (foam.isDead() || !foam.isValid()) { cancel(); return; }
                                    foam.getWorld().spawnParticle(Particle.SNOWFLAKE, foam.getLocation(), 3, 0.05, 0.05, 0.05, 0);
                                }
                            }.runTaskTimer(MolochnicaEvent.this, 0L, 1L);
                        }
                    }
                }.runTaskLater(MolochnicaEvent.this, 60L); // Выстрел через 3 сек после удара по земле

            }
        }.runTaskTimer(this, 0L, 140L); // Цикл каждые 7 секунд
    }

    private Player getTopDamager() {
        UUID topUUID = null;
        double maxDamage = -1;
        for (Map.Entry<UUID, Double> entry : damageMap.entrySet()) {
            if (entry.getValue() > maxDamage) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    maxDamage = entry.getValue();
                    topUUID = entry.getKey();
                }
            }
        }
        return topUUID != null ? Bukkit.getPlayer(topUUID) : null;
    }

    // Кастомный просчет урона: 3 ХП голому, 1.5 ХП фулл незер з4
    private void dealCustomDamage(Player p, double baseDamage) {
        double finalDamage = baseDamage;
        boolean hasArmor = false;
        
        // Проверяем, есть ли на игроке хоть какая-то броня
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR) {
                hasArmor = true;
                break;
            }
        }
        
        // Если игрок в броне, снижаем урон ровно до 1.5 ХП
        if (hasArmor) {
            finalDamage = 1.5;
        }

        p.damage(finalDamage);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (bossInstance == null || !e.getEntity().getUniqueId().equals(bossInstance.getUniqueId())) return;

        // Запись урона игроков по боссу
        if (e.getDamager() instanceof Player) {
            Player p = (Player) e.getDamager();
            damageMap.put(p.getUniqueId(), damageMap.getOrDefault(p.getUniqueId(), 0.0) + e.getFinalDamage());
        }
    }

    @EventHandler
    public void onFoamHit(EntityDamageByEntityEvent e) {
        // Попадание пеной (снежком) в игрока
        if (e.getDamager() instanceof Snowball && e.getEntity() instanceof Player) {
            Snowball s = (Snowball) e.getDamager();
            if ("foam_projectile".equals(s.getCustomName())) {
                Player p = (Player) e.getEntity();
                dealCustomDamage(p, 3.0);
                e.setCancelled(true); // Отмена стандартного сбивания анимации снежком
            }
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        if (bossInstance == null || !e.getEntity().getUniqueId().equals(bossInstance.getUniqueId())) return;

        Player killer = e.getEntity().getKiller();
        
        // Выдача 50к монет убийце
        if (killer != null && econ != null) {
            econ.depositPlayer(killer, 50000);
            killer.sendMessage("§aВы убили Молочницу и получили §e50,000$§a!");
        }

        // Разлет ресурсов (Выпадают во все стороны фонтаном)
        Location loc = e.getEntity().getLocation();
        Material[] drops = {Material.DIAMOND, Material.NETHERITE_INGOT, Material.GOLD_INGOT};
        
        for (Material mat : drops) {
            for (int i = 0; i < 5; i++) {
                ItemStack item = new ItemStack(mat, 1);
                org.bukkit.entity.Item droppedItem = loc.getWorld().dropItem(loc, item);
                // Задаем случайный вектор разлета
                double x = (Math.random() - 0.5) * 0.5;
                double y = 0.4 + Math.random() * 0.3;
                double z = (Math.random() - 0.5) * 0.5;
                droppedItem.setVelocity(new Vector(x, y, z));
            }
        }
        bossInstance = null;
    }
}
