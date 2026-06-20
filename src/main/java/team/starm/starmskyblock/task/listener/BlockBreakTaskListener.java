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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockBreakTaskListener extends BaseTaskListener {

    /** chunkKey(long) -> blockKey(long) 的集合 */
    private final Map<Long, Set<Long>> playerPlacedBlocks = new ConcurrentHashMap<>();

    private static final Map<String, Integer> WORLD_INDICES = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_WORLD_INDEX = new AtomicInteger();

    public BlockBreakTaskListener(TaskManager taskManager) {
        super(taskManager, TaskType.BLOCK_BREAK);
        startCleanupTask();
    }

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
        // 被抹掉 → 误判为自然方块的竞态。
        boolean[] removed = { false };
        playerPlacedBlocks.computeIfPresent(ck, (k, set) -> {
            removed[0] = set.remove(bk);
            return set.isEmpty() ? null : set;
        });
        boolean isNatural = !removed[0];

        taskManager.incrementNaturalProgress(player, TaskType.BLOCK_BREAK,
                block.getType().name(), 1, isNatural);
    }

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

    private static long chunkKey(String worldName, int cx, int cz) {
        int worldIndex = WORLD_INDICES.computeIfAbsent(
                worldName, k -> NEXT_WORLD_INDEX.getAndIncrement());
        if ((worldIndex & 0xFFFF) != worldIndex) {
            throw new IllegalStateException("worldIndex exceeds 16 bits: " + worldIndex);
        }
        // 16-bit worldIndex | 16-bit chunkX | 16-bit chunkZ = 48 bits, fits in a long.
        // chunkX/Z cover ±32767 chunks = ±524272 blocks, far beyond island sizes.
        return ((long) (worldIndex & 0xFFFF) << 32)
             | ((long) (cx & 0xFFFF) << 16)
             | (cz & 0xFFFFL);
    }

    private static long blockKey(Block block) {
        int y = block.getY() + 128;
        return ((long) (block.getX() & 0xF) << 14)
             | ((long) (y & 0x3FF) << 4)
             | (block.getZ() & 0xF);
    }

    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(StarMSkyblock.getInstance(), () -> {
            playerPlacedBlocks.values().removeIf(Set::isEmpty);
        }, 6000L, 6000L);
    }
}