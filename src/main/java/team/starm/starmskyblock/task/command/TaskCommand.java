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

/**
 * /is task 子命令
 * <p>
 * 路由玩家任务相关操作：{@code list}（列出章节/任务，可按章节号过滤）、
 * {@code submit}（手动提交 ITEM 型任务物品）、{@code claim}（领取已完成任务奖励）。
 * submit/claim 均以 {@code <章节号> <任务号>} 定位任务。无子命令时等价于 list。
 * </p>
 */
public class TaskCommand extends SubCommand {

    public TaskCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 分发子命令。
     *
     * @param player 执行者
     * @param args   参数，args[1] 为子命令名
     * @return 是否已处理
     */
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

    /**
     * 渲染任务列表文本。
     * <p>按章节顺序输出，每章节先判锁定，再逐任务按状态选择图标：
     * 章节或任务未解锁为锁、已 claim 为完成、可领取（非 ITEM 且已完成未 claim）为感叹号、否则为未完成。
     * ITEM 型任务不显示"可领取"标记（需手动 submit）。</p>
     *
     * @param chapterFilter 章节号过滤，{@code -1} 表示不过滤
     */
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
                    // ITEM 型需手动 submit，不展示"可领取"感叹号标记
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

    /**
     * Tab 补全：第二参数补 list/submit/claim，其后补章节号、再补任务号。
     */
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

    /** 不区分大小写的前缀过滤 */
    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream()
                .filter(s -> s != null && s.toLowerCase().startsWith(lower))
                .toList();
    }
}
