package team.starm.starmskyblock.task.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlockBreakTaskListener extends BaseTaskListener {

    private final Set<String> playerPlacedBlocks = ConcurrentHashMap.newKeySet();

    public BlockBreakTaskListener(TaskManager taskManager) {
        super(taskManager, TaskType.BLOCK_BREAK);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        playerPlacedBlocks.add(locationKey(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        Block block = event.getBlock();
        String key = locationKey(block);
        boolean isNatural = !playerPlacedBlocks.remove(key);
        taskManager.incrementNaturalProgress(player, TaskType.BLOCK_BREAK,
                block.getType().name(), 1, isNatural);
    }

    private static String locationKey(Block block) {
        return block.getWorld().getName() + "," + block.getX() + ","
                + block.getY() + "," + block.getZ();
    }
}
