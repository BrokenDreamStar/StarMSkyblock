package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.task.TaskDefinition;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.config.TaskConfigScanner;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class SetTaskCommand extends AdminSubCommand {

    public SetTaskCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 5) {
            MessageUtil.sendMessage(sender, "&c用法: /isadmin settask <玩家> <章节编号> <任务编号> complete|reset");
            return true;
        }

        String playerName = args[1];
        String action = args[4].toLowerCase();

        if (!action.equals("complete") && !action.equals("reset")) {
            MessageUtil.sendMessage(sender, "&c操作必须是 complete 或 reset！");
            return true;
        }

        int chapterNumber;
        int missionNumber;
        try {
            chapterNumber = Integer.parseInt(args[2]);
            missionNumber = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "&c章节编号和任务编号必须是数字！");
            return true;
        }

        UUID targetUuid = resolvePlayer(playerName);
        if (targetUuid == null) {
            MessageUtil.sendMessage(sender, "&c找不到玩家 " + playerName + "！");
            return true;
        }

        TaskConfigScanner taskConfig = plugin.getTaskManager().getTaskConfig();
        TaskDefinition def = taskConfig.getTaskByChapterAndMission(chapterNumber, missionNumber);
        if (def == null) {
            MessageUtil.sendMessage(sender, "&c找不到章节 " + chapterNumber + " 的第 " + missionNumber + " 个任务！");
            return true;
        }

        String fullTaskId = def.getId();
        TaskManager taskManager = plugin.getTaskManager();

        if (action.equals("complete")) {
            taskManager.adminForceComplete(targetUuid, fullTaskId);
            MessageUtil.sendMessage(sender, "&a已强制完成玩家 &e" + playerName + " &a的任务 &e" + def.getName() + " &a(第" + chapterNumber + "章 第" + missionNumber + "个)");
        } else {
            taskManager.adminResetTask(targetUuid, fullTaskId);
            MessageUtil.sendMessage(sender, "&a已重置玩家 &e" + playerName + " &a的任务 &e" + def.getName() + " &a(第" + chapterNumber + "章 第" + missionNumber + "个)");
        }

        return true;
    }

    private UUID resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            String prefix = args[2];
            return plugin.getTaskManager().getTaskConfig().getCategories().values().stream()
                    .map(cat -> String.valueOf(cat.getChapterNumber()))
                    .filter(n -> n.startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (args.length == 4) {
            String prefix = args[3];
            try {
                int chapterNumber = Integer.parseInt(args[2]);
                var category = plugin.getTaskManager().getTaskConfig().getCategoryByChapterNumber(chapterNumber);
                if (category != null) {
                    List<String> missionNumbers = new ArrayList<>();
                    for (TaskDefinition def : category.getTasks()) {
                        missionNumbers.add(String.valueOf(def.getMissionNumber()));
                    }
                    return missionNumbers.stream()
                            .filter(n -> n.startsWith(prefix))
                            .collect(Collectors.toList());
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (args.length == 5) {
            String prefix = args[4].toLowerCase();
            List<String> actions = List.of("complete", "reset");
            return actions.stream()
                    .filter(a -> a.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
