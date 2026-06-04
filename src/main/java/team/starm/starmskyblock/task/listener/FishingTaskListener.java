package team.starm.starmskyblock.task.listener;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerFishEvent;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskType;

public class FishingTaskListener extends BaseTaskListener {

    public FishingTaskListener(TaskManager taskManager) {
        super(taskManager, TaskType.FISHING);
    }

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
