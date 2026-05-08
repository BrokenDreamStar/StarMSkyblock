package team.starm.starmskyblock.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Monster;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.island.IslandSettings;
import team.starm.starmskyblock.message.MessageUtil;

public class IslandSettingsListener implements Listener {

    private final IslandManager islandManager;
    private final ConfigManager configManager;

    public IslandSettingsListener(IslandManager islandManager, ConfigManager configManager) {
        this.islandManager = islandManager;
        this.configManager = configManager;
    }

    // ====================== PVP ======================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        Player damager = getPlayerDamager(event.getDamager());
        if (damager == null) {
            return;
        }

        Location location = target.getLocation();
        Island island = getIslandAt(location);
        if (island == null) {
            return;
        }

        if (!island.getSettingsObject().isPvp()) {
            event.setCancelled(true);
            MessageUtil.sendMessage(damager, "&e岛屿保护 &f|&c 该岛屿已禁用PVP！");
        }
    }

    // ====================== 生物生成 ======================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        Location location = event.getLocation();

        Island island = getIslandAt(location);
        if (island == null) {
            return;
        }

        IslandSettings settings = island.getSettingsObject();

        // 刷怪笼生成的生物
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            if (!settings.isSpawnerSpawn()) {
                event.setCancelled(true);
            }
            return;
        }

        // 自然生成
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.DEFAULT) {
            if (entity instanceof Monster && !settings.isMonsterSpawn()) {
                event.setCancelled(true);
            } else if (entity instanceof Animals && !settings.isAnimalSpawn()) {
                event.setCancelled(true);
            }
        }
    }

    // ====================== 火势蔓延 ======================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() != Material.FIRE
                && event.getSource().getType() != Material.SOUL_FIRE) {
            return;
        }

        Island island = getIslandAt(event.getBlock().getLocation());
        if (island == null) {
            return;
        }

        if (!island.getSettingsObject().isFireSpread()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Island island = getIslandAt(event.getBlock().getLocation());
        if (island == null) {
            return;
        }

        if (!island.getSettingsObject().isFireSpread()) {
            event.setCancelled(true);
        }
    }

    // ====================== 末影人 / 凋灵 搬运/破坏方块 ======================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();

        Island island = getIslandAt(event.getBlock().getLocation());
        if (island == null) {
            return;
        }

        IslandSettings settings = island.getSettingsObject();

        if (entity instanceof Enderman && !settings.isEndermanGrief()) {
            event.setCancelled(true);
        } else if (entity instanceof Wither && !settings.isWitherGrief()) {
            event.setCancelled(true);
        }
    }

    // ====================== 爆炸 ======================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity == null) {
            return;
        }

        Island island = getIslandAt(event.getLocation());
        if (island == null) {
            return;
        }

        IslandSettings settings = island.getSettingsObject();

        if (entity instanceof Creeper && !settings.isCreeperExplosion()) {
            event.setCancelled(true);
        } else if ((entity instanceof TNTPrimed || entity instanceof ExplosiveMinecart) && !settings.isTntExplosion()) {
            event.setCancelled(true);
        } else if (entity instanceof Fireball fireball && fireball.getShooter() instanceof Ghast
                && !settings.isGhastFireballGrief()) {
            event.setCancelled(true);
        } else if (entity instanceof Wither && !settings.isWitherGrief()) {
            event.setCancelled(true);
        } else if (entity instanceof WitherSkull && !settings.isWitherGrief()) {
            event.setCancelled(true);
        }
    }

    // ====================== 辅助方法 ======================

    private Island getIslandAt(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }

        String worldName = world.getName();
        if (!worldName.equals(configManager.getWorldNameNormal())
                && !worldName.equals(configManager.getWorldNameNether())
                && !worldName.equals(configManager.getWorldNameEnd())) {
            return null;
        }

        int chunkX = location.getChunk().getX();
        int chunkZ = location.getChunk().getZ();

        for (Island island : islandManager.getAllIslands()) {
            if (island.isChunkWithinIsland(chunkX, chunkZ)) {
                return island;
            }
        }
        return null;
    }

    private Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        } else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }
}
