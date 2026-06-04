package team.starm.starmskyblock.task.command;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.command.subcommand.SubCommand;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.task.TaskCategory;
import team.starm.starmskyblock.task.TaskDefinition;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskProgress;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TaskCommand extends SubCommand {

    public TaskCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        TaskManager taskManager = plugin.getTaskManager();

        if (args.length == 1) {
            showTaskList(player, taskManager, null);
            return true;
        }

        String sub = args[1].toLowerCase();

        if (sub.equals("list")) {
            String category = args.length >= 3 ? args[2] : null;
            showTaskList(player, taskManager, category);
            return true;
        }

        if (sub.equals("claim")) {
            if (args.length < 3) {
                MessageUtil.sendMessage(player, "&c用法: /is task claim <任务ID>");
                return true;
            }
            taskManager.claimTask(player, args[2]);
            return true;
        }

        if (sub.equals("info")) {
            if (args.length < 3) {
                MessageUtil.sendMessage(player, "&c用法: /is task info <任务ID>");
                return true;
            }
            showTaskInfo(player, taskManager, args[2]);
            return true;
        }

        MessageUtil.sendMessage(player, "&c未知的子命令！可用: list, claim, info");
        return true;
    }

    private void showTaskList(Player player, TaskManager taskManager, String categoryFilter) {
        UUID uuid = player.getUniqueId();
        Map<String, TaskCategory> categories = taskManager.getTaskConfig().getCategories();

        MessageUtil.sendMessage(player, "&a=== 任务列表 ===");

        for (TaskCategory cat : categories.values()) {
            if (categoryFilter != null && !cat.getId().equalsIgnoreCase(categoryFilter)) continue;

            MessageUtil.sendMessage(player, "");
            MessageUtil.sendMessage(player, "  &b▶ " + cat.getName());

            for (TaskDefinition def : cat.getTasks()) {
                TaskProgress prog = taskManager.getPlayerProgressMap(uuid).get(def.getId());
                boolean completed = prog != null && prog.getCompletedCount() > 0 && prog.isClaimed();
                boolean canComplete = prog != null && !prog.isClaimed() && prog.isCompleted(def);
                boolean locked = !taskManager.isTaskUnlocked(uuid, def.getId());

                String icon;
                if (locked) {
                    icon = "&8🔒";
                } else if (completed) {
                    icon = "&a✔";
                } else if (canComplete) {
                    icon = "&e⚠";
                } else {
                    icon = "&7✘";
                }

                int pct = prog != null ? (int) Math.round(prog.getProgressPercent(def) * 100) : 0;
                MessageUtil.sendMessage(player, "    " + icon + " &f" + def.getName()
                        + " &7[" + pct + "%]"
                        + " &7(&f" + def.getId() + "&7)");
            }
        }

        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&e/is task claim <任务ID> &7- 领取奖励");
        MessageUtil.sendMessage(player, "&e/is task info <任务ID> &7- 查看任务详情");
    }

    private void showTaskInfo(Player player, TaskManager taskManager, String taskId) {
        TaskDefinition def = taskManager.getTaskConfig().getTask(taskId);
        if (def == null) {
            MessageUtil.sendMessage(player, "&c任务 " + taskId + " 不存在！");
            return;
        }

        UUID uuid = player.getUniqueId();
        TaskProgress prog = taskManager.getPlayerProgressMap(uuid).get(taskId);

        MessageUtil.sendMessage(player, "&a=== 任务详情: " + def.getName() + " ===");
        MessageUtil.sendMessage(player, "  &7分类: " + def.getCategoryId());
        MessageUtil.sendMessage(player, "  &7类型: " + def.getTaskType().name());

        if (!def.getRequiredMissionIds().isEmpty()) {
            boolean unlocked = taskManager.isTaskUnlocked(uuid, taskId);
            MessageUtil.sendMessage(player, "  &7前置任务: " + (unlocked ? "&a✔" : "&c✘"));
        }

        MessageUtil.sendMessage(player, "  &7需求:");
        for (TaskDefinition.RequirementGroup req : def.getRequirements()) {
            int current = 0;
            if (prog != null && prog.getProgress() != null) {
                for (String type : req.getTypes()) {
                    current += prog.getProgress().getOrDefault(type.toUpperCase(), 0);
                }
            }
            String typesStr = String.join(", ", req.getTypes());
            int shown = Math.min(current, req.getAmount());
            MessageUtil.sendMessage(player, "    &e" + typesStr + "&7: " + shown + "/" + req.getAmount());
        }

        int pct = prog != null ? (int) Math.round(prog.getProgressPercent(def) * 100) : 0;
        MessageUtil.sendMessage(player, "  &7进度: &e" + pct + "%");

        if (prog != null && !prog.isClaimed() && prog.isCompleted(def)) {
            MessageUtil.sendMessage(player, "  &a✔ 可领取奖励！使用 /is task claim " + taskId);
        } else if (prog != null && prog.isClaimed()) {
            MessageUtil.sendMessage(player, "  &a✔ 已完成 (已领取)");
        } else {
            MessageUtil.sendMessage(player, "  &c✘ 未完成");
        }

        if (!def.getRewards().isEmpty()) {
            MessageUtil.sendMessage(player, "  &7奖励:");
            for (String cmd : def.getRewards().commands()) {
                MessageUtil.sendMessage(player, "    &e" + cmd.replace("%player%", player.getName()));
            }
        }
    }

    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        if (args.length == 2) {
            return filterPrefix(List.of("list", "claim", "info"), args[1]);
        }

        if (args.length == 3 && (args[1].equalsIgnoreCase("claim") || args[1].equalsIgnoreCase("info"))) {
            TaskManager taskManager = plugin.getTaskManager();
            return filterPrefix(new ArrayList<>(taskManager.getTaskConfig().getTaskIndex().keySet()), args[2]);
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("list")) {
            return filterPrefix(new ArrayList<>(plugin.getTaskManager().getTaskConfig().getCategories().keySet()), args[2]);
        }

        return List.of();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream()
                .filter(s -> s != null && s.toLowerCase().startsWith(lower))
                .toList();
    }
}
