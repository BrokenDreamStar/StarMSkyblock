package team.starm.starmskyblock.task.placeholder;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.task.TaskCategory;
import team.starm.starmskyblock.task.TaskDefinition;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskProgress;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 任务系统 PlaceholderAPI 占位符处理器
 * <p>
 * 处理以 {@code task_} 为前缀的占位符，支持任务级与章节级查询。
 * 任务通过 {@code <章节号>_<任务号>} 定位，章节通过章节号定位。
 * 支持的占位符形态：
 * <ul>
 *   <li>{@code task_<c>_<m>_percentage_<key>} -- 某需求类型当前百分比</li>
 *   <li>{@code task_<c>_<m>_value_<key>} -- 某需求类型当前数量</li>
 *   <li>{@code task_completed_count} / {@code task_total_count} -- 全局完成数/总数</li>
 *   <li>{@code task_<c>_<m>_progress} / {@code task_<c>_progress} -- 任务/章节进度百分比</li>
 *   <li>{@code task_<c>_<m>_completed} / {@code task_<c>_completed} -- 是否完成（未领取）</li>
 *   <li>{@code task_<c>_<m>_claimed} -- 是否已领取奖励</li>
 *   <li>{@code task_<c>_<m>_count} -- 任务已完成次数</li>
 * </ul>
 * </p>
 */
public class TaskPlaceholderHandler {

    /** 占位符前缀，调用方需先以此过滤再分发到本处理器 */
    public static final String PREFIX = "task_";

    private final StarMSkyblock plugin;

    public TaskPlaceholderHandler(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * 解析并返回占位符对应的值。
     * <p>注意查询顺序：含 {@code _percentage_} / {@code _value_} 的中缀模式必须先于
     * {@code _progress} 等后缀模式匹配，否则会被后缀分支错误截断。任何异常返回 null（占位符不替换）。</p>
     *
     * @param player 查询玩家
     * @param params 占位符完整参数（含前缀）
     * @return 对应值字符串，无法解析返回 null
     */
    public String handle(Player player, String params) {
        try {
            TaskManager taskManager = plugin.getTaskManager();
            if (taskManager == null) return null;

            String rest = params.substring(PREFIX.length());

            // _percentage_ and _value_ use indexOf - check before endsWith patterns
            int pctIdx = rest.indexOf("_percentage_");
            if (pctIdx > 0) {
                String id = rest.substring(0, pctIdx);
                String key = rest.substring(pctIdx + "_percentage_".length()).toUpperCase();
                TaskDefinition def = resolveTask(id);
                if (def != null) return getPercentage(player, def, key);
            }

            int valueIdx = rest.indexOf("_value_");
            if (valueIdx > 0) {
                String id = rest.substring(0, valueIdx);
                String key = rest.substring(valueIdx + "_value_".length()).toUpperCase();
                TaskDefinition def = resolveTask(id);
                if (def != null) return getValue(player, def, key);
            }

            // Global counts (exact match, no prefix)
            if (rest.equals("completed_count")) {
                return String.valueOf(getTotalCompletedCount(player));
            }

            if (rest.equals("total_count")) {
                return String.valueOf(taskManager.getTaskConfig().getTaskIndex().size());
            }

            // endsWith checks
            if (rest.endsWith("_progress")) {
                String id = rest.substring(0, rest.length() - "_progress".length());
                TaskDefinition def = resolveTask(id);
                if (def != null) return getProgress(player, def);
                TaskCategory cat = resolveChapter(id);
                if (cat != null) return String.valueOf(getChapterProgress(player, cat));
            }

            if (rest.endsWith("_completed")) {
                String id = rest.substring(0, rest.length() - "_completed".length());
                TaskDefinition def = resolveTask(id);
                if (def != null) return isCompleted(player, def) ? "true" : "false";
                TaskCategory cat = resolveChapter(id);
                if (cat != null) return isChapterCompleted(player, cat) ? "true" : "false";
            }

            if (rest.endsWith("_claimed")) {
                String id = rest.substring(0, rest.length() - "_claimed".length());
                TaskDefinition def = resolveTask(id);
                if (def != null) return isClaimed(player, def) ? "true" : "false";
            }

            if (rest.endsWith("_count")) {
                String id = rest.substring(0, rest.length() - "_count".length());
                TaskDefinition def = resolveTask(id);
                if (def != null) return String.valueOf(getCompletedCount(player, def));
            }

        } catch (Throwable ignored) {}

        return null;
    }

    /** 按 {@code <章节号>_<任务号>} 解析任务，格式不符返回 null */
    private TaskDefinition resolveTask(String id) {
        String[] parts = id.split("_");
        if (parts.length != 2) return null;
        try {
            int chapter = Integer.parseInt(parts[0]);
            int mission = Integer.parseInt(parts[1]);
            return plugin.getTaskManager().getTaskConfig().getTaskByChapterAndMission(chapter, mission);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 按章节号解析章节，非数字返回 null */
    private TaskCategory resolveChapter(String id) {
        try {
            int chapter = Integer.parseInt(id);
            return plugin.getTaskManager().getTaskConfig().getCategoryByChapterNumber(chapter);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 任务是否已完成但未领取奖励（claimable 状态） */
    private boolean isCompleted(Player player, TaskDefinition def) {
        if (player == null || def == null) return false;
        UUID uuid = player.getUniqueId();
        TaskManager taskManager = plugin.getTaskManager();
        TaskProgress prog = taskManager.getPlayerProgressMap(uuid).get(def.getId());
        if (prog == null) return false;
        return prog.isCompleted(def) && !prog.isClaimed();
    }

    /** 任务奖励是否已领取 */
    private boolean isClaimed(Player player, TaskDefinition def) {
        if (player == null || def == null) return false;
        return plugin.getTaskManager().isTaskCompleted(player.getUniqueId(), def.getId());
    }

    /** 章节内所有任务是否均已领取奖励 */
    private boolean isChapterCompleted(Player player, TaskCategory category) {
        if (player == null || category == null) return false;
        UUID uuid = player.getUniqueId();
        for (TaskDefinition task : category.getTasks()) {
            if (!plugin.getTaskManager().isTaskCompleted(uuid, task.getId())) {
                return false;
            }
        }
        return true;
    }

    /** 全部任务中已领取奖励的数量 */
    private int getTotalCompletedCount(Player player) {
        if (player == null) return 0;
        UUID uuid = player.getUniqueId();
        Collection<TaskDefinition> allTasks = plugin.getTaskManager().getTaskConfig().getTaskIndex().values();
        int count = 0;
        for (TaskDefinition task : allTasks) {
            if (plugin.getTaskManager().isTaskCompleted(uuid, task.getId())) {
                count++;
            }
        }
        return count;
    }

    /** 任务整体完成百分比（0~100 字符串） */
    private String getProgress(Player player, TaskDefinition def) {
        if (player == null || def == null) return "0";
        UUID uuid = player.getUniqueId();
        TaskManager taskManager = plugin.getTaskManager();
        TaskProgress prog = taskManager.getPlayerProgressMap(uuid).get(def.getId());
        if (prog == null) return "0";
        return String.valueOf((int) Math.round(prog.getProgressPercent(def) * 100));
    }

    /** 章节内各任务完成百分比的平均值（0~100） */
    private int getChapterProgress(Player player, TaskCategory category) {
        if (player == null || category == null || category.getTasks().isEmpty()) return 0;
        UUID uuid = player.getUniqueId();
        TaskManager taskManager = plugin.getTaskManager();
        double total = 0;
        for (TaskDefinition task : category.getTasks()) {
            TaskProgress prog = taskManager.getPlayerProgressMap(uuid).get(task.getId());
            if (prog != null) {
                total += prog.getProgressPercent(task);
            }
        }
        return (int) Math.round(total / category.getTasks().size() * 100);
    }

    /** 任务已完成次数 */
    private int getCompletedCount(Player player, TaskDefinition def) {
        if (player == null || def == null) return 0;
        TaskProgress prog = plugin.getTaskManager().getPlayerProgressMap(player.getUniqueId()).get(def.getId());
        return prog != null ? prog.getCompletedCount() : 0;
    }

    /** 任务某需求类型的当前累计数量（字符串） */
    private String getValue(Player player, TaskDefinition def, String key) {
        if (player == null || def == null) return "0";
        UUID uuid = player.getUniqueId();
        TaskProgress prog = plugin.getTaskManager().getPlayerProgressMap(uuid).get(def.getId());
        if (prog == null || prog.getProgress() == null) return "0";
        return String.valueOf(prog.getProgress().getOrDefault(key, 0));
    }

    /** 任务某需求类型相对目标数量的百分比（上限 100） */
    private String getPercentage(Player player, TaskDefinition def, String key) {
        if (player == null || def == null) return "0";
        UUID uuid = player.getUniqueId();
        TaskManager taskManager = plugin.getTaskManager();
        TaskProgress prog = taskManager.getPlayerProgressMap(uuid).get(def.getId());
        if (prog == null || prog.getProgress() == null) return "0";

        for (TaskDefinition.RequirementGroup req : def.getRequirements()) {
            int reqAmount = req.getAmount();
            int current = 0;
            for (String type : req.getTypes()) {
                if (type.equalsIgnoreCase(key)) {
                    current += prog.getProgress().getOrDefault(type.toUpperCase(), 0);
                }
            }
            if (current > 0) {
                return String.valueOf(Math.min(100, (int) Math.round(current * 100.0 / reqAmount)));
            }
        }
        return "0";
    }
}
