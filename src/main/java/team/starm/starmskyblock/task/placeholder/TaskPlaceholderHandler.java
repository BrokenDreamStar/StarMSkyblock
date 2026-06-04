package team.starm.starmskyblock.task.placeholder;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.task.TaskDefinition;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskProgress;

import java.util.Map;
import java.util.UUID;

public class TaskPlaceholderHandler {

    public static final String PREFIX = "task_";

    private final StarMSkyblock plugin;

    public TaskPlaceholderHandler(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public String handle(Player player, String params) {
        try {
            TaskManager taskManager = plugin.getTaskManager();
            if (taskManager == null) return null;

            String rest = params.substring(PREFIX.length());

            if (rest.endsWith("_progress")) {
                String taskId = rest.substring(0, rest.length() - "_progress".length());
                return getProgress(player, taskId);
            }

            if (rest.endsWith("_completed")) {
                String taskId = rest.substring(0, rest.length() - "_completed".length());
                return isCompleted(player, taskId) ? "是" : "否";
            }

            if (rest.endsWith("_count")) {
                String taskId = rest.substring(0, rest.length() - "_count".length());
                return String.valueOf(getCompletedCount(player, taskId));
            }

            int valueIdx = rest.indexOf("_value_");
            if (valueIdx > 0) {
                String taskId = rest.substring(0, valueIdx);
                String key = rest.substring(valueIdx + "_value_".length()).toUpperCase();
                return getValue(player, taskId, key);
            }

            int pctIdx = rest.indexOf("_percentage_");
            if (pctIdx > 0) {
                String taskId = rest.substring(0, pctIdx);
                String key = rest.substring(pctIdx + "_percentage_".length()).toUpperCase();
                return getPercentage(player, taskId, key);
            }

            if (rest.endsWith("_completed_count")) {
                return null;
            }

            if (rest.endsWith("_total_count")) {
                return null;
            }

        } catch (Throwable ignored) {}

        return null;
    }

    private boolean isCompleted(Player player, String taskId) {
        if (player == null) return false;
        return plugin.getTaskManager().isTaskCompleted(player.getUniqueId(), taskId);
    }

    private String getProgress(Player player, String taskId) {
        if (player == null) return "0";
        UUID uuid = player.getUniqueId();
        TaskManager taskManager = plugin.getTaskManager();
        TaskDefinition def = taskManager.getTaskConfig().getTask(taskId);
        if (def == null) return "0";

        TaskProgress prog = taskManager.getPlayerProgressMap(uuid).get(taskId);
        if (prog == null) return "0";

        return String.valueOf((int) Math.round(prog.getProgressPercent(def) * 100));
    }

    private int getCompletedCount(Player player, String taskId) {
        if (player == null) return 0;
        TaskProgress prog = plugin.getTaskManager().getPlayerProgressMap(player.getUniqueId()).get(taskId);
        return prog != null ? prog.getCompletedCount() : 0;
    }

    private String getValue(Player player, String taskId, String key) {
        if (player == null) return "0";
        UUID uuid = player.getUniqueId();
        TaskProgress prog = plugin.getTaskManager().getPlayerProgressMap(uuid).get(taskId);
        if (prog == null || prog.getProgress() == null) return "0";
        return String.valueOf(prog.getProgress().getOrDefault(key, 0));
    }

    private String getPercentage(Player player, String taskId, String key) {
        if (player == null) return "0";
        UUID uuid = player.getUniqueId();
        TaskManager taskManager = plugin.getTaskManager();
        TaskDefinition def = taskManager.getTaskConfig().getTask(taskId);
        if (def == null) return "0";

        TaskProgress prog = taskManager.getPlayerProgressMap(uuid).get(taskId);
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
