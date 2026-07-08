package team.starm.starmskyblock.task.command;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.command.subcommand.SubCommand;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.task.TaskCategory;
import team.starm.starmskyblock.task.TaskDefinition;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskProgress;
import team.starm.starmskyblock.task.TaskType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class TaskCommand extends SubCommand {

    public TaskCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        TaskManager taskManager = plugin.getTaskManager();

        Optional<Island> islandOpt = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (islandOpt.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        if (args.length == 1) {
            showTaskList(player, taskManager, -1);
            return true;
        }

        String sub = args[1].toLowerCase();

        if (sub.equals("list")) {
            int chapterFilter = -1;
            if (args.length >= 3) {
                try {
                    chapterFilter = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    MessageUtil.send(player, "task.chapter-not-number");
                    return true;
                }
            }
            showTaskList(player, taskManager, chapterFilter);
            return true;
        }

        if (sub.equals("submit")) {
            if (args.length < 4) {
                MessageUtil.send(player, "task.submit.usage");
                return true;
            }
            try {
                int chapter = Integer.parseInt(args[2]);
                int mission = Integer.parseInt(args[3]);
                TaskDefinition def = taskManager.getTaskConfig().getTaskByChapterAndMission(chapter, mission);
                if (def == null) {
                    MessageUtil.send(player, "task.not-found", Map.of("chapter", chapter, "mission", mission));
                    return true;
                }
                taskManager.submitItems(player, def.getId());
            } catch (NumberFormatException e) {
                MessageUtil.send(player, "task.chapter-mission-not-number");
            }
            return true;
        }

        if (sub.equals("claim")) {
            if (args.length < 4) {
                MessageUtil.send(player, "task.claim.usage");
                return true;
            }
            try {
                int chapter = Integer.parseInt(args[2]);
                int mission = Integer.parseInt(args[3]);
                TaskDefinition def = taskManager.getTaskConfig().getTaskByChapterAndMission(chapter, mission);
                if (def == null) {
                    MessageUtil.send(player, "task.not-found", Map.of("chapter", chapter, "mission", mission));
                    return true;
                }
                taskManager.claimTask(player, def.getId());
            } catch (NumberFormatException e) {
                MessageUtil.send(player, "task.chapter-mission-not-number");
            }
            return true;
        }

        MessageUtil.send(player, "task.unknown-subcommand");
        return true;
    }

    private void showTaskList(Player player, TaskManager taskManager, int chapterFilter) {
        UUID uuid = player.getUniqueId();
        Map<String, TaskCategory> categories = taskManager.getTaskConfig().getCategories();

        MessageUtil.send(player, "task.list.header");

        for (TaskCategory cat : categories.values()) {
            if (chapterFilter > 0 && cat.getChapterNumber() != chapterFilter) continue;

            boolean chapterLocked = !taskManager.isChapterUnlocked(uuid, cat.getId());
            String chapterIcon = chapterLocked ? "&8🔒" : "&b▶";
            MessageUtil.send(player, "general.empty-line");
            MessageUtil.send(player, "task.list.chapter-entry",
                    Map.of("icon", chapterIcon, "name", cat.getName(), "id", cat.getChapterNumber()));

            for (TaskDefinition def : cat.getTasks()) {
                boolean locked = chapterLocked || !taskManager.isTaskUnlocked(uuid, def.getId());

                String icon;
                if (locked) {
                    icon = "&8🔒";
                } else {
                    TaskProgress prog = taskManager.getPlayerProgressMap(uuid).get(def.getId());
                    boolean completed = prog != null && prog.isClaimed();
                    boolean canComplete = def.getTaskType() == TaskType.ITEM
                            ? false
                            : (prog != null && !prog.isClaimed() && prog.isCompleted(def));
                    if (completed) {
                        icon = "&a✔";
                    } else if (canComplete) {
                        icon = "&e⚠";
                    } else {
                        icon = "&7✘";
                    }
                }

                int missionNum = def.getMissionNumber();
                int chapterNum = cat.getChapterNumber();
                TaskProgress prog = taskManager.getPlayerProgressMap(uuid).get(def.getId());
                int pct = (prog != null && !locked) ? (int) Math.round(prog.getProgressPercent(def) * 100) : 0;
                MessageUtil.send(player, "task.list.task-entry",
                        Map.of("number", missionNum, "name", def.getName(), "pct", pct, "icon", icon));
            }
        }

        MessageUtil.send(player, "general.empty-line");
        MessageUtil.send(player, "task.list.footer-submit");
        MessageUtil.send(player, "task.list.footer-claim");
    }

    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        TaskManager taskManager = plugin.getTaskManager();

        if (args.length == 2) {
            return filterPrefix(List.of("list", "submit", "claim"), args[1]);
        }

        if (args.length == 3) {
            List<String> chapterNumbers = taskManager.getTaskConfig().getCategories().values().stream()
                    .map(cat -> String.valueOf(cat.getChapterNumber()))
                    .collect(Collectors.toList());
            if (args[1].equalsIgnoreCase("list")) {
                return filterPrefix(chapterNumbers, args[2]);
            }
            if (args[1].equalsIgnoreCase("submit") || args[1].equalsIgnoreCase("claim")) {
                return filterPrefix(chapterNumbers, args[2]);
            }
        }

        if (args.length == 4 && (args[1].equalsIgnoreCase("submit")
                || args[1].equalsIgnoreCase("claim"))) {
            try {
                int chapter = Integer.parseInt(args[2]);
                TaskCategory cat = taskManager.getTaskConfig().getCategoryByChapterNumber(chapter);
                if (cat != null) {
                    List<String> missionNumbers = cat.getTasks().stream()
                            .map(def -> String.valueOf(def.getMissionNumber()))
                            .collect(Collectors.toList());
                    return filterPrefix(missionNumbers, args[3]);
                }
            } catch (NumberFormatException ignored) {}
            return List.of();
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
