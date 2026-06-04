package team.starm.starmskyblock.task.listener;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskType;

public abstract class BaseTaskListener implements org.bukkit.event.Listener {

    protected final TaskManager taskManager;
    protected final TaskType taskType;

    public BaseTaskListener(TaskManager taskManager, TaskType taskType) {
        this.taskManager = taskManager;
        this.taskType = taskType;
    }

    protected void track(Player player, String key, int amount) {
        taskManager.incrementProgress(player, taskType, key, amount);
    }
}
