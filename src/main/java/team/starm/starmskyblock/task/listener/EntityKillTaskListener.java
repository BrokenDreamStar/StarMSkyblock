package team.starm.starmskyblock.task.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskType;

/**
 * ENTITY_KILL 任务监听器
 * <p>
 * 实体死亡时若存在击杀者（玩家），按实体类型名计入进度。
 * 自然死亡/无玩家击杀者的事件被跳过。
 * </p>
 */
public class EntityKillTaskListener extends BaseTaskListener {

    public EntityKillTaskListener(TaskManager taskManager) {
        super(taskManager, TaskType.ENTITY_KILL);
    }

    /** 仅当存在玩家击杀者时计入，键为实体类型名 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        track(killer, event.getEntityType().name(), 1);
    }
}
