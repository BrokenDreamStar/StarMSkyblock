package team.starm.starmskyblock.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.command.subcommand.*;
import team.starm.starmskyblock.task.command.TaskCommand;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.*;
import java.util.stream.Collectors;

public class IslandCommand implements CommandExecutor, TabCompleter {

    private final StarMSkyblock plugin;
    private final IslandPermissionCommand permissionCommand;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    private final List<String> subCommandNames = new ArrayList<>();

    public IslandCommand(StarMSkyblock plugin) {
        this.plugin = plugin;
        this.permissionCommand = new IslandPermissionCommand(plugin);
    }

    public void registerCommands() {
        subCommands.put("create", new CreateCommand(plugin));
        subCommands.put("spawn", new SpawnCommand(plugin));
        subCommands.put("tp", new TpCommand(plugin));
        subCommands.put("setspawn", new SetSpawnCommand(plugin));
        subCommands.put("border", new BorderCommand(plugin));
        subCommands.put("delete", new DeleteCommand(plugin));
        subCommands.put("info", new InfoCommand(plugin));
        SubCommand promoteDemote = new PromoteDemoteCommand(plugin);
        subCommands.put("promote", promoteDemote);
        subCommands.put("team", new TeamCommand(plugin));
        subCommands.put("demote", promoteDemote);

        subCommands.put("coops", new MembersInfoCommand(plugin));
        subCommands.put("mycoops", new MembersInfoCommand(plugin));
        subCommands.put("myperms", new MembersInfoCommand(plugin));
        subCommands.put("role", new MembersInfoCommand(plugin));
        subCommands.put("rename", new RenameCommand(plugin));
        subCommands.put("coop", new CoopCommand(plugin));
        subCommands.put("list", new ListCommand(plugin));
        subCommands.put("settings", new SettingsCommand(plugin));
        subCommands.put("setchunkbiome", new SetChunkBiomeCommand(plugin));
        subCommands.put("setbiome", new SetBiomeCommand(plugin));
        subCommands.put("generator", new GeneratorCommand(plugin));
        subCommands.put("upgrade", new UpgradeCommand(plugin));
        subCommands.put("task", new TaskCommand(plugin));
        subCommands.put("level", new LevelCommand(plugin));
        subCommands.put("transfer", new TransferCommand(plugin));

        subCommands.put("help", new HelpCommand(plugin, this));
        subCommands.put("portalinfo", new PortalInfoCommand(plugin));
        subCommandNames.addAll(subCommands.keySet());
        subCommandNames.add("permission");
        Collections.sort(subCommandNames); // keep sorted for help/tab
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "general.player-only");
            return true;
        }

        if (!sender.hasPermission("skyblock.is")) {
            MessageUtil.send(sender, "general.no-permission");
            return true;
        }

        boolean silent = args.length > 0 && (args[args.length - 1].equals("-s"));
        if (silent) {
            args = java.util.Arrays.copyOf(args, args.length - 1);
            MessageUtil.setSilent(player.getUniqueId(), true);
        }

        try {
            return handleCommand(player, args);
        } finally {
            if (silent) {
                MessageUtil.setSilent(player.getUniqueId(), false);
            }
        }
    }

    private boolean handleCommand(Player player, String[] args) {
        if (args.length == 0) {
            String defaultCmd = plugin.getConfigManager().getDefaultIslandCommand();
            SubCommand subCommand = subCommands.get(defaultCmd);
            if (subCommand != null) {
                return subCommand.execute(player, new String[]{defaultCmd});
            }
            sendHelpMessage(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        SubCommand subCommand = subCommands.get(sub);
        if (subCommand != null) {
            return subCommand.execute(player, args);
        }

        if (sub.equals("permission")) {
            return permissionCommand.handlePermissionCommand(player, args);
        }

        MessageUtil.send(player, "command.is.unknown");
        return true;
    }

    public void sendHelpMessage(Player player) {
        MessageUtil.send(player, "help.header");
        MessageUtil.send(player, "help.entry.create");
        MessageUtil.send(player, "help.entry.tp");
        MessageUtil.send(player, "help.entry.rename");
        MessageUtil.send(player, "help.entry.spawn");
        MessageUtil.send(player, "help.entry.setspawn");
        MessageUtil.send(player, "help.entry.border");
        MessageUtil.send(player, "help.entry.delete");
        MessageUtil.send(player, "help.entry.info");
        MessageUtil.send(player, "help.entry.team-invite");
        MessageUtil.send(player, "help.entry.team-remove");
        MessageUtil.send(player, "help.entry.team-accept");
        MessageUtil.send(player, "help.entry.team-decline");
        MessageUtil.send(player, "help.entry.team-list");
        MessageUtil.send(player, "help.entry.coop-add");
        MessageUtil.send(player, "help.entry.coop-remove");
        MessageUtil.send(player, "help.entry.coops");
        MessageUtil.send(player, "help.entry.mycoops");
//        MessageUtil.send(player, "help.entry.list");
        MessageUtil.send(player, "help.entry.permission");
        MessageUtil.send(player, "help.entry.promote");
        MessageUtil.send(player, "help.entry.demote");
        MessageUtil.send(player, "help.entry.myperms");
        MessageUtil.send(player, "help.entry.role");
        MessageUtil.send(player, "help.entry.settings");
        MessageUtil.send(player, "help.entry.settings-set");
        MessageUtil.send(player, "help.entry.setchunkbiome");
        MessageUtil.send(player, "help.entry.setbiome");
        MessageUtil.send(player, "help.entry.generator");
        MessageUtil.send(player, "help.entry.upgrade");
        MessageUtil.send(player, "help.entry.level");
        MessageUtil.send(player, "help.entry.transfer");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return subCommandNames.stream()
                    .filter(sub -> sub.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(sub);
        if (subCommand != null) {
            return subCommand.onTabComplete(player, args);
        }

        if (sub.equals("permission")) {
            List<String> permissionCompletions = permissionCommand.onTabComplete(args);
            if (permissionCompletions != null) {
                return permissionCompletions;
            }
        }

        return new ArrayList<>();
    }

}
