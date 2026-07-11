package team.starm.starmskyblock.listener;

import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import team.starm.starmskyblock.world.SkyblockWorldManager;

/**
 * 末地保护监听器
 * <p>
 * 在空岛末地世界中阻止末影龙的自然生成与末地水晶的实体生成，
 * 避免玩家利用末影龙战斗或末地水晶破坏岛屿结构。仅对空岛世界生效。
 * </p>
 */
public class EndProtectionListener implements Listener {

    /** 世界管理器，用于判定生成位置是否在空岛世界内 */
    private final SkyblockWorldManager worldManager;

    public EndProtectionListener(SkyblockWorldManager worldManager) {
        this.worldManager = worldManager;
    }

    /**
     * 监听生物生成事件
     * <p>
     * 拦截末影龙在空岛世界中的自然生成（如末地重生机制），取消其生成事件。
     * </p>
     */
    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.ENDER_DRAGON) return;
        World world = event.getEntity().getWorld();
        if (worldManager.isSkyblockWorld(world)) {
            event.setCancelled(true);
        }
    }

    /**
     * 监听实体生成事件
     * <p>
     * 拦截末地水晶在空岛世界中的生成（包括末影龙复活流程中的水晶），取消其生成事件。
     * </p>
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntityType() != EntityType.END_CRYSTAL) return;
        World world = event.getEntity().getWorld();
        if (worldManager.isSkyblockWorld(world)) {
            event.setCancelled(true);
        }
    }
}
