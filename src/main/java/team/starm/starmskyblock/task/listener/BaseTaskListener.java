package team.starm.starmskyblock.task.listener;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskType;

import org.bukkit.event.Listener;

/**
 * 任务监听器基类
 * <p>
 * 所有 {@code TaskType} 对应事件监听器的公共父类，持有 {@link TaskManager} 与自身类型，
 * 提供统一的 {@link #track} 转发方法。具体子类监听对应 Bukkit 事件后调用 {@link #track}
 * 即可计入玩家进度。{@link TaskManager} 在启动时按配置中出现的类型选择性注册子类实例。
 * </p>
 */
public abstract class BaseTaskListener implements Listener {

    /** 任务管理器，进度计入与配置查询的统一入口 */
    protected final TaskManager taskManager;
    /** 本监听器对应的任务类型，决定 {@link #track} 写入的进度分类 */
    protected final TaskType taskType;

    public BaseTaskListener(TaskManager taskManager, TaskType taskType) {
        this.taskManager = taskManager;
        this.taskType = taskType;
    }

    /**
     * 计入一次自然进度（非手动提交）。
     *
     * @param player 触发事件的玩家
     * @param key    进度键（材料名/实体类型名等，内部会大写化）
     * @param amount 本次增量
     */
    protected void track(Player player, String key, int amount) {
        taskManager.incrementProgress(player, taskType, key, amount);
    }
}
