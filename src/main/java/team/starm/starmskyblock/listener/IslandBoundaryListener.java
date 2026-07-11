package team.starm.starmskyblock.listener;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.block.BlockState;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.Optional;

/**
 * 岛屿边界保护监听器。
 * <p>
 * 阻止水流、活塞、树木生长、树叶衰减等环境方块变化事件
 * 在岛屿当前解锁半径之外（锁定区域或公共区域）生成方块。
 * 玩家主动操作（放置/破坏等）已由权限系统处理，此监听器仅处理非玩家触发的连锁变化。
 * </p>
 */
public class IslandBoundaryListener implements Listener {

    /** 岛屿管理器，根据区块坐标查询所属岛屿 */
    private final IslandManager islandManager;
    /** 世界管理器，判定方块是否处于空岛世界 */
    private final SkyblockWorldManager worldManager;
    /** 配置管理器（保留供后续边界规则扩展使用） */
    private final ConfigManager configManager;

    public IslandBoundaryListener(IslandManager islandManager, SkyblockWorldManager worldManager,
                                  ConfigManager configManager) {
        this.islandManager = islandManager;
        this.worldManager = worldManager;
        this.configManager = configManager;
    }

    /**
     * 液体流动 — 检查目标方块位置是否在岛屿边界内
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (isOutsideIslandArea(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * 活塞推动 — 检查被推动的每个方块的目的地是否在岛屿边界内
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            Location target = block.getLocation().add(event.getDirection().getDirection());
            if (isOutsideIslandArea(target)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * 活塞收回（黏性活塞拉回方块） — 检查每个方块被拉到的位置
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!event.isSticky()) return;

        for (Block block : event.getBlocks()) {
            Location target = block.getLocation().add(event.getDirection().getOppositeFace().getDirection());
            if (isOutsideIslandArea(target)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * 结构生长（树木、巨型蘑菇、紫颂树） — 仅移除越界的方块，岛屿内的生长不受影响
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        event.getBlocks().removeIf(blockState -> isOutsideIslandArea(blockState.getLocation()));
    }

    /**
     * 树叶自然衰减
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (isOutsideIslandArea(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * 方块融化/褪色（冰融化、雪融化、耕地退化等）
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (isOutsideIslandArea(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * 方块蔓延（藤蔓、蘑菇、火等）
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (isOutsideIslandArea(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * 实体创造方块（雪傀儡铺雪、青蛙产卵等） — 检查生成位置是否在岛屿边界内
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        if (isOutsideIslandArea(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * 判断目标位置是否在岛屿当前解锁半径之外。
     * <p>
     * 返回 true 的情况：
     * <ul>
     *   <li>位置在非空岛世界 → false（放行，不做保护）</li>
     *   <li>位置在空岛世界但没有所属岛屿（公共区域） → true（应阻止）</li>
     *   <li>位置属于某个岛屿但不在当前解锁半径内（锁定区域） → true（应阻止）</li>
     *   <li>位置在岛屿当前解锁半径内 → false（放行）</li>
     * </ul>
     * </p>
     */
    private boolean isOutsideIslandArea(Location location) {
        World world = location.getWorld();
        if (world == null) return false;

        if (!worldManager.isSkyblockWorld(world)) return false;

        // 位运算取区块坐标，避免 location.getChunk() 在区块未驻留时触发主线程同步加载
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;

        Optional<Island> islandOpt = islandManager.getIslandAt(chunkX, chunkZ);

        if (islandOpt.isEmpty()) {
            return true;
        }

        return !islandOpt.get().isChunkWithinIsland(chunkX, chunkZ);
    }
}
