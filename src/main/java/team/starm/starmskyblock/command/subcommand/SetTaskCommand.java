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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class SetTaskCommand extends AdminSubCommand {

    public SetTaskCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 5) {
            MessageUtil.send(sender, "task.set.usage");
            return true;
        }

        String playerName = args[1];
        String action = args[4].toLowerCase();

        if (!action.equals("complete") && !action.equals("reset")) {
            MessageUtil.send(sender, "task.set.invalid-action");
            return true;
        }

        int chapterNumber;
        int missionNumber;
        try {
            chapterNumber = Integer.parseInt(args[2]);
            missionNumber = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, "task.set.not-number");
            return true;
        }

        UUID targetUuid = resolvePlayer(playerName);
        if (targetUuid == null) {
            MessageUtil.send(sender, "task.set.player-not-found", Map.of("name", playerName));
            return true;
        }

        TaskConfigScanner taskConfig = plugin.getTaskManager().getTaskConfig();
        TaskDefinition def = taskConfig.getTaskByChapterAndMission(chapterNumber, missionNumber);
        if (def == null) {
            MessageUtil.send(sender, "task.set.task-not-found",
                    Map.of("chapter", chapterNumber, "mission", missionNumber));
            return true;
        }

        String fullTaskId = def.getId();
        TaskManager taskManager = plugin.getTaskManager();

        if (action.equals("complete")) {
            taskManager.adminForceComplete(targetUuid, fullTaskId);
            MessageUtil.send(sender, "task.set.complete-success", Map.of(
                    "player", playerName,
                    "task", def.getName(),
                    "chapter", chapterNumber,
                    "mission", missionNumber
            ));
        } else {
            taskManager.adminResetTask(targetUuid, fullTaskId);
            MessageUtil.send(sender, "task.set.reset-success", Map.of(
                    "player", playerName,
                    "task", def.getName(),
                    "chapter", chapterNumber,
                    "mission", missionNumber
            ));
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
