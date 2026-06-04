package team.starm.starmskyblock.task.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskType;

public class EntityKillTaskListener extends BaseTaskListener {

    public EntityKillTaskListener(TaskManager taskManager) {
        super(taskManager, TaskType.ENTITY_KILL);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        track(killer, event.getEntityType().name(), 1);
    }
}
