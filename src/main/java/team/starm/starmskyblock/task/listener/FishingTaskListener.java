package team.starm.starmskyblock.task.listener;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerFishEvent;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskType;

/**
 * FISHING 任务监听器
 * <p>
 * 玩家钓获鱼/物品时按钓获物材料名计入进度。仅 {@code CAUGHT_FISH} 状态触发，
 * 其他钓鱼阶段（抛竿、收竿失败等）被忽略。
 * </p>
 */
public class FishingTaskListener extends BaseTaskListener {

    public FishingTaskListener(TaskManager taskManager) {
        super(taskManager, TaskType.FISHING);
    }

    /** 仅钓获物品时计入，键为钓获物的材料名 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();
        if (player == null) return;

        if (event.getCaught() instanceof Item item) {
            track(player, item.getItemStack().getType().name(), 1);
        }
    }
}
