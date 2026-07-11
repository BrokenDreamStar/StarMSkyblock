package team.starm.starmskyblock.task.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskType;

/**
 * BLOCK_PLACE 任务监听器
 * <p>
 * 玩家放置方块时按材料名计入进度。注意此监听器仅做进度累计，不区分自然/人工放置
 * （该区分仅在 BLOCK_BREAK 任务中通过 only-natural 生效）。
 * </p>
 */
public class BlockPlaceTaskListener extends BaseTaskListener {

    public BlockPlaceTaskListener(TaskManager taskManager) {
        super(taskManager, TaskType.BLOCK_PLACE);
    }

    /** MONITOR 优先级 + ignoreCancelled：事件未被取消且方块实际放置后才计入进度 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        track(player, event.getBlock().getType().name(), 1);
    }
}
