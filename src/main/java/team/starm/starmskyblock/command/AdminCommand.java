package team.starm.starmskyblock.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.command.subcommand.AdminSubCommand;
import team.starm.starmskyblock.command.subcommand.BypassCommand;
import team.starm.starmskyblock.command.subcommand.ReloadCommand;
import team.starm.starmskyblock.command.subcommand.SetGeneratorCommand;
import team.starm.starmskyblock.command.subcommand.SetRadiusCommand;
import team.starm.starmskyblock.command.subcommand.SetTaskCommand;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final StarMSkyblock plugin;
    private final Map<String, AdminSubCommand> subCommands = new LinkedHashMap<>();
    private final List<String> subCommandNames = new ArrayList<>();

    public AdminCommand(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        subCommands.put("setradius", new SetRadiusCommand(plugin));
        subCommands.put("setgenerator", new SetGeneratorCommand(plugin));
        subCommands.put("settask", new SetTaskCommand(plugin));
        subCommands.put("reload", new ReloadCommand(plugin));
        subCommands.put("bypass", new BypassCommand(plugin));
        subCommandNames.addAll(subCommands.keySet());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skyblock.admin")) {
            MessageUtil.send(sender, "general.no-permission");
            return true;
        }

        boolean silent = false;
        Player player = sender instanceof Player ? (Player) sender : null;

        if (player != null && args.length > 0 && args[args.length - 1].equals("-s")) {
            silent = true;
            args = java.util.Arrays.copyOf(args, args.length - 1);
            MessageUtil.setSilent(player.getUniqueId(), true);
        }

        try {
            if (args.length == 0) {
                sendUsage(sender);
                return true;
            }

            AdminSubCommand sub = subCommands.get(args[0].toLowerCase());
            if (sub != null) {
                return sub.execute(sender, args);
            }

            sendUsage(sender);
            return true;
        } finally {
            if (silent && player != null) {
                MessageUtil.setSilent(player.getUniqueId(), false);
            }
        }
    }

    private void sendUsage(CommandSender sender) {
        MessageUtil.send(sender, "command.admin.usage-header");
        MessageUtil.send(sender, "command.admin.usage-setradius");
        MessageUtil.send(sender, "command.admin.usage-setgenerator");
        MessageUtil.send(sender, "command.admin.usage-settask");
        MessageUtil.send(sender, "command.admin.usage-reload");
        MessageUtil.send(sender, "command.admin.usage-bypass");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, @NonNull Command command, @NonNull String alias, String[] args) {
        if (!sender.hasPermission("skyblock.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return subCommandNames.stream()
                    .filter(name -> name.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        AdminSubCommand sub = subCommands.get(args[0].toLowerCase());
        if (sub != null) {
            return sub.onTabComplete(sender, args);
        }

        return new ArrayList<>();
    }
}
