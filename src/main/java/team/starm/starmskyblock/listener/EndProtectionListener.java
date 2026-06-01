package team.starm.starmskyblock.listener;

import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import team.starm.starmskyblock.world.SkyblockWorldManager;

public class EndProtectionListener implements Listener {

    private final SkyblockWorldManager worldManager;

    public EndProtectionListener(SkyblockWorldManager worldManager) {
        this.worldManager = worldManager;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.ENDER_DRAGON) return;
        World world = event.getEntity().getWorld();
        if (worldManager.isSkyblockWorld(world)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntityType() != EntityType.END_CRYSTAL) return;
        World world = event.getEntity().getWorld();
        if (worldManager.isSkyblockWorld(world)) {
            event.setCancelled(true);
        }
    }
}
