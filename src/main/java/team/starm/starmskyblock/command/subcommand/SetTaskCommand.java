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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class SetTaskCommand extends AdminSubCommand {

    public SetTaskCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 5) {
            MessageUtil.sendMessage(sender, "&c用法: /isadmin settask <玩家> <章节ID> <任务ID> complete|reset");
            return true;
        }

        String playerName = args[1];
        String chapterId = args[2];
        String taskName = args[3];
        String action = args[4].toLowerCase();

        if (!action.equals("complete") && !action.equals("reset")) {
            MessageUtil.sendMessage(sender, "&c操作必须是 complete 或 reset！");
            return true;
        }

        UUID targetUuid = resolvePlayer(playerName);
        if (targetUuid == null) {
            MessageUtil.sendMessage(sender, "&c找不到玩家 " + playerName + "！");
            return true;
        }

        String fullTaskId = chapterId + "/" + taskName;

        TaskConfigScanner taskConfig = plugin.getTaskManager().getTaskConfig();
        TaskDefinition def = taskConfig.getTask(fullTaskId);
        if (def == null) {
            MessageUtil.sendMessage(sender, "&c找不到任务 " + fullTaskId + "！");
            return true;
        }

        TaskManager taskManager = plugin.getTaskManager();

        if (action.equals("complete")) {
            taskManager.adminForceComplete(targetUuid, fullTaskId);
            MessageUtil.sendMessage(sender, "&a已强制完成玩家 &e" + playerName + " &a的任务 &e" + def.getName() + " &a(" + fullTaskId + ")");
        } else {
            taskManager.adminResetTask(targetUuid, fullTaskId);
            MessageUtil.sendMessage(sender, "&a已重置玩家 &e" + playerName + " &a的任务 &e" + def.getName() + " &a(" + fullTaskId + ")");
        }

        return true;
    }

    private UUID resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        Optional<UUID> offline = plugin.getPlayerRepo().getUUID(name);
        return offline.orElse(null);
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
            String prefix = args[2].toLowerCase();
            return plugin.getTaskManager().getTaskConfig().getCategories().keySet().stream()
                    .filter(id -> id.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 4) {
            String chapterId = args[2];
            String prefix = args[3].toLowerCase();
            var category = plugin.getTaskManager().getTaskConfig().getCategories().get(chapterId);
            if (category != null) {
                List<String> taskNames = new ArrayList<>();
                for (TaskDefinition def : category.getTasks()) {
                    String id = def.getId();
                    String shortName = id.substring(id.indexOf('/') + 1);
                    taskNames.add(shortName);
                }
                return taskNames.stream()
                        .filter(n -> n.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList());
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
