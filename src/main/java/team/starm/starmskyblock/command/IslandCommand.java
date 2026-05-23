package team.starm.starmskyblock.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.command.subcommand.*;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.setting.IslandSetting;
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
        registerCommands();
    }

    private void registerCommands() {
        subCommands.put("create", new CreateCommand(plugin));
        subCommands.put("home", new HomeCommand(plugin));
        subCommands.put("tp", new TpCommand(plugin));
        subCommands.put("sethome", new SetHomeCommand(plugin));
        subCommands.put("border", new BorderCommand(plugin));
        subCommands.put("delete", new DeleteCommand(plugin));
        subCommands.put("invite", new InviteCommand(plugin));
        subCommands.put("remove", new RemoveCommand(plugin));
        subCommands.put("promote", new PromoteDemoteCommand(plugin));
        subCommands.put("demote", new PromoteDemoteCommand(plugin));
        subCommands.put("members", new MembersInfoCommand(plugin));
        subCommands.put("coops", new MembersInfoCommand(plugin));
        subCommands.put("mycoops", new MembersInfoCommand(plugin));
        subCommands.put("myperms", new MembersInfoCommand(plugin));
        subCommands.put("role", new MembersInfoCommand(plugin));
        subCommands.put("rename", new RenameCommand(plugin));
        subCommands.put("coop", new CoopCommand(plugin));
        subCommands.put("settings", new SettingsCommand(plugin));
        subCommands.put("accept", new AcceptDeclineCommand(plugin));
        subCommands.put("decline", new AcceptDeclineCommand(plugin));
        subCommandNames.addAll(subCommands.keySet());
        Collections.sort(subCommandNames); // keep sorted for help/tab
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, "&c只有玩家才能执行该命令！");
            return true;
        }

        if (!sender.hasPermission("skyblock.is")) {
            MessageUtil.sendMessage(sender, "&c你没有权限执行此命令！");
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
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
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

        MessageUtil.sendMessage(player, "&c未知命令。输入 /is help 查看帮助。");
        return true;
    }

    private void sendHelpMessage(Player player) {
        MessageUtil.sendMessage(player, "&a=== StarMSkyblock 帮助 ===");
        MessageUtil.sendMessage(player, "&b/is create [类型] [名称] &f- 创建你的空岛");
        MessageUtil.sendMessage(player, "&b/is home [confirm] &f- 传送到你的空岛（confirm强制传送）");
        MessageUtil.sendMessage(player, "&b/is tp <岛屿名称> [岛屿ID] [confirm] &f- 传送到指定岛屿");
        MessageUtil.sendMessage(player, "&b/is sethome &f- 设置岛屿传送点（脚下不能是空气）");
        MessageUtil.sendMessage(player, "&b/is border [true|false|toggle] &f- 开启/关闭/切换岛屿边界显示");
        MessageUtil.sendMessage(player, "&b/is delete [confirm] &f- 删除你的空岛");
        MessageUtil.sendMessage(player, "&b/is invite <玩家> &f- 邀请玩家加入岛屿");
        MessageUtil.sendMessage(player, "&b/is accept &f- 接受岛屿邀请");
        MessageUtil.sendMessage(player, "&b/is decline &f- 拒绝岛屿邀请");
        MessageUtil.sendMessage(player, "&b/is remove <玩家> [confirm] &f- 踢出岛屿成员");
        MessageUtil.sendMessage(player, "&b/is promote <玩家> &f- 晋升成员角色");
        MessageUtil.sendMessage(player, "&b/is demote <玩家> &f- 降级成员角色");
        MessageUtil.sendMessage(player, "&b/is members &f- 查看岛屿成员列表");
        MessageUtil.sendMessage(player, "&b/is coops &f- 查看合作者列表");
        MessageUtil.sendMessage(player, "&b/is mycoops &f- 查看自己是哪些岛屿的合作者");
        MessageUtil.sendMessage(player, "&b/is role &f- 查看自己的岛屿角色");
        MessageUtil.sendMessage(player, "&b/is rename <名称> &f- 修改岛屿名称");
        MessageUtil.sendMessage(player, "&b/is coop add <玩家> &f- 添加合作者");
        MessageUtil.sendMessage(player, "&b/is coop remove <玩家> &f- 移除合作者");
        MessageUtil.sendMessage(player, "&b/is myperms &f- 查看自己拥有的权限");
        MessageUtil.sendMessage(player, "&b/is permission <权限> <等级> &f- 设置权限最低等级");
        MessageUtil.sendMessage(player, "&b/is settings &f- 查看岛屿设置");
        MessageUtil.sendMessage(player, "&b/is settings <设置项> <true|false> &f- 修改岛屿设置");
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

        if (args.length == 2 && args[0].equalsIgnoreCase("border")) {
            return filterPrefix(List.of("true", "false", "toggle"), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("settings")) {
            return filterPrefix(
                    Arrays.stream(IslandSetting.values()).map(IslandSetting::getConfigKey).toList(),
                    args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("settings")) {
            return filterPrefix(List.of("true", "false"), args[2]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("coop")) {
            return filterPrefix(List.of("add", "remove"), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("coop") && args[1].equalsIgnoreCase("add")) {
            var islandManager = plugin.getIslandManager();
            var optionalIsland = islandManager.getIsland(player.getUniqueId());
            if (optionalIsland.isPresent()) {
                Island island = optionalIsland.get();
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> {
                            Player p = Bukkit.getPlayer(name);
                            if (p == null || p.getUniqueId().equals(player.getUniqueId())) return false;
                            if (island.isCoop(p.getUniqueId())) return false;
                            return islandManager.getIslandByPlayer(p.getUniqueId()).isPresent();
                        })
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("coop") && args[1].equalsIgnoreCase("remove")) {
            var optionalIsland = plugin.getIslandManager().getIsland(player.getUniqueId());
            if (optionalIsland.isPresent()) {
                return optionalIsland.get().getCoops().stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            var optionalIsland = plugin.getIslandManager().getIsland(player.getUniqueId());
            if (optionalIsland.isPresent()) {
                Island island = optionalIsland.get();
                return filterPrefix(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> {
                            Player p = Bukkit.getPlayer(name);
                            if (p == null || p.getUniqueId().equals(player.getUniqueId())) return false;
                            return island.getMemberRole(p.getUniqueId()) == IslandPermissionLevel.VISITOR;
                        })
                        .toList(), args[1]);
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            return filterPrefix(plugin.getIslandManager().getAllIslands().stream()
                    .map(Island::getName)
                    .filter(name -> name != null && !name.isEmpty())
                    .distinct()
                    .toList(), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("tp")) {
            String name = args[1];
            var matches = plugin.getIslandManager().getIslandsByName(name);
            if (matches.size() > 1) {
                return filterPrefix(matches.stream()
                        .map(i -> String.valueOf(i.getId()))
                        .toList(), args[2]);
            }
        }

        if (args.length == 2) {
            String cmd = args[0].toLowerCase();
            if (cmd.equals("remove") || cmd.equals("promote") || cmd.equals("demote")) {
                var optionalIsland = plugin.getIslandManager().getIsland(player.getUniqueId());
                if (optionalIsland.isPresent()) {
                    Island island = optionalIsland.get();
                    IslandPermissionLevel executorRole = island.getMemberRole(player.getUniqueId());
                    return filterPrefix(island.getMembers().entrySet().stream()
                            .filter(e -> e.getValue().getPermissionLevel() < executorRole.getPermissionLevel())
                            .map(e -> getPlayerName(e.getKey()))
                            .filter(Objects::nonNull)
                            .toList(), args[1]);
                }
            }
        }

        List<String> permissionCompletions = permissionCommand.onTabComplete(args);
        if (permissionCompletions != null) {
            return permissionCompletions;
        }

        return new ArrayList<>();
    }

    private String getPlayerName(java.util.UUID uuid) {
        var name = plugin.getSqliteManager().getPlayerName(uuid);
        return name.orElse(uuid.toString());
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream()
                .filter(s -> s != null && s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
