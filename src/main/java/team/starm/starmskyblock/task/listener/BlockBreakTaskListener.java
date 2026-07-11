package team.starm.starmskyblock.task.listener;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskType;
import team.starm.starmskyblock.util.BlockCoordKeys;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BLOCK_BREAK 任务监听器
 * <p>
 * 破坏方块时按材料名计入进度。为支持 {@code only-natural}（仅记自然方块），
 * 本监听器同时监听 {@link BlockPlaceEvent} 记录玩家放置的方块坐标，破坏时若命中记录则不计入自然进度。
 * 坐标记录以 48-bit 复合键压缩存入 {@link #playerPlacedBlocks}，区块卸载时按 chunk 批量清理。
 * </p>
 */
public class BlockBreakTaskListener extends BaseTaskListener {

    /** chunkKey(long) -> blockKey(long) 的集合 */
    private final Map<Long, Set<Long>> playerPlacedBlocks = new ConcurrentHashMap<>();

    /** 世界名 -> 16 位索引，用于 chunkKey 编码区分不同世界（最多 65536 个世界） */
    private static final Map<String, Integer> WORLD_INDICES = new ConcurrentHashMap<>();
    /** 下一个可用世界索引，单调递增 */
    private static final AtomicInteger NEXT_WORLD_INDEX = new AtomicInteger();

    public BlockBreakTaskListener(TaskManager taskManager) {
        super(taskManager, TaskType.BLOCK_BREAK);
        startCleanupTask();
    }

    /** MONITOR 记录玩家放置的方块，供破坏时判定是否人工放置 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        playerPlacedBlocks.computeIfAbsent(chunkKey(block), k -> ConcurrentHashMap.newKeySet())
                .add(blockKey(block));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        Block block = event.getBlock();
        long ck = chunkKey(block);
        long bk = blockKey(block);

        // 用 computeIfPresent 原子化"移除方块键 + 空集清理"，避免原实现
        // `inner.isEmpty()` 与 `remove(ck)` 之间另一线程 `computeIfAbsent().add()`
        // 被抹掉 -> 误判为自然方块的竞态。
        boolean[] removed = { false };
        playerPlacedBlocks.computeIfPresent(ck, (k, set) -> {
            removed[0] = set.remove(bk);
            return set.isEmpty() ? null : set;
        });
        boolean isNatural = !removed[0];

        taskManager.incrementNaturalProgress(player, TaskType.BLOCK_BREAK,
                block.getType().name(), 1, isNatural);
    }

    /** 区块卸载时丢弃其放置记录，避免内存随探索无限增长 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        playerPlacedBlocks.remove(chunkKey(event.getChunk()));
    }

    private static long chunkKey(Block block) {
        return chunkKey(block.getWorld().getName(), block.getX() >> 4, block.getZ() >> 4);
    }

    private static long chunkKey(Chunk chunk) {
        return chunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    /**
     * 将世界+区块坐标编码为 48-bit long 复合键。
     * <p>16-bit worldIndex | 16-bit chunkX | 16-bit chunkZ。
     * chunkX/Z 覆盖 ±32767 区块 = ±524272 方块，远超岛屿尺寸。</p>
     */
    private static long chunkKey(String worldName, int cx, int cz) {
        int worldIndex = WORLD_INDICES.computeIfAbsent(
                worldName, k -> NEXT_WORLD_INDEX.getAndIncrement());
        // 编解码抽至 BlockCoordKeys（可单测）；worldIndex 分配仍在此处维护
        return BlockCoordKeys.encodeChunkKey(worldIndex, cx, cz);
    }

    /**
     * 将区块内方块坐标编码为 long 键：4-bit x | 10-bit (y+128) | 4-bit z。
     * <p>y 偏移 128 以容纳负高度（世界底 y=-64 对应 64）。</p>
     */
    private static long blockKey(Block block) {
        return BlockCoordKeys.encodeBlockKey(block.getX(), block.getY(), block.getZ());
    }

    /** 定期清理已变为空集的 chunk 键条目，回收 Map 节点 */
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(StarMSkyblock.getInstance(), () -> {
            playerPlacedBlocks.values().removeIf(Set::isEmpty);
        }, 6000L, 6000L);
    }
}
