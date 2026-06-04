package team.starm.starmskyblock.task.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskType;

public class BlockBreakTaskListener extends BaseTaskListener {

    public BlockBreakTaskListener(TaskManager taskManager) {
        super(taskManager, TaskType.BLOCK_BREAK);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        track(player, event.getBlock().getType().name(), 1);
    }
}
