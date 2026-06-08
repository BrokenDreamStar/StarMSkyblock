package team.starm.starmskyblock.task.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskType;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlockBreakTaskListener extends BaseTaskListener {

    /** 玩家放置的方块：外层 key 为 world,chunkX,chunkZ，内层 Set 为 chunk 内的 x,y,z */
    private final Map<String, Set<String>> playerPlacedBlocks = new ConcurrentHashMap<>();

    public BlockBreakTaskListener(TaskManager taskManager) {
        super(taskManager, TaskType.BLOCK_BREAK);
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
        String ck = chunkKey(block);
        Set<String> inner = playerPlacedBlocks.get(ck);
        boolean isNatural = true;
        if (inner != null) {
            isNatural = !inner.remove(blockKey(block));
            if (inner.isEmpty()) {
                playerPlacedBlocks.remove(ck);
            }
        }
        taskManager.incrementNaturalProgress(player, TaskType.BLOCK_BREAK,
                block.getType().name(), 1, isNatural);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        String ck = event.getWorld().getName() + ","
                + event.getChunk().getX() + "," + event.getChunk().getZ();
        playerPlacedBlocks.remove(ck);
    }

    private static String chunkKey(Block block) {
        return block.getWorld().getName() + ","
                + (block.getX() >> 4) + "," + (block.getZ() >> 4);
    }

    private static String blockKey(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }
}
