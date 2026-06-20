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

        subCommands.put("help", new HelpCommand(plugin, this));
        subCommands.put("portalinfo", new PortalInfoCommand(plugin));
        subCommandNames.addAll(subCommands.keySet());
        subCommandNames.add("permission");
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

        MessageUtil.sendMessage(player, "&c未知命令。输入 /is help 查看帮助。");
        return true;
    }

    public void sendHelpMessage(Player player) {
        MessageUtil.sendMessage(player, "&a=== <gradient:#14bcfe:#495aff>&lStarM Skyblock</gradient>&a帮助 ===");
        MessageUtil.sendMessage(player, "&b/is create [类型] [名称] &f- 创建岛屿");
        MessageUtil.sendMessage(player, "&b/is tp <岛屿名称> [岛屿ID]&f- 传送到指定岛屿");
        MessageUtil.sendMessage(player, "&b/is rename <名称> &f- 修改岛屿名称");
        MessageUtil.sendMessage(player, "&b/is spawn &f- 传送回岛屿");
        MessageUtil.sendMessage(player, "&b/is setspawn &f- 设置岛屿传送点");
        MessageUtil.sendMessage(player, "&b/is border [true|false|toggle] &f- 开启/关闭/切换岛屿边界显示");
        MessageUtil.sendMessage(player, "&b/is delete &f- 删除岛屿");
        MessageUtil.sendMessage(player, "&b/is info &f- 查看岛屿信息");
        MessageUtil.sendMessage(player, "&b/is team invite <玩家> &f- 邀请玩家加入岛屿");
        MessageUtil.sendMessage(player, "&b/is team remove <玩家> [confirm] &f- 移除岛屿成员");
        MessageUtil.sendMessage(player, "&b/is team accept &f- 接受岛屿邀请");
        MessageUtil.sendMessage(player, "&b/is team decline &f- 拒绝岛屿邀请");
        MessageUtil.sendMessage(player, "&b/is team list &f- 查看岛屿成员列表");
        MessageUtil.sendMessage(player, "&b/is coop add <玩家> &f- 添加合作者");
        MessageUtil.sendMessage(player, "&b/is coop remove <玩家> &f- 移除合作者");
        MessageUtil.sendMessage(player, "&b/is coops &f- 查看合作者列表");
        MessageUtil.sendMessage(player, "&b/is mycoops &f- 查看自己是哪些岛屿的合作者");
//        MessageUtil.sendMessage(player, "&b/is list [next|prev|home] &f- 翻页浏览岛屿列表");
        MessageUtil.sendMessage(player, "&b/is permission <权限> <等级> &f- 设置权限最低等级");
        MessageUtil.sendMessage(player, "&b/is promote <玩家> &f- 晋升成员权限组");
        MessageUtil.sendMessage(player, "&b/is demote <玩家> &f- 降级成员权限组");
        MessageUtil.sendMessage(player, "&b/is myperms &f- 查看自己拥有的权限");
        MessageUtil.sendMessage(player, "&b/is role &f- 查看自己的岛屿权限组");
        MessageUtil.sendMessage(player, "&b/is settings &f- 查看岛屿设置");
        MessageUtil.sendMessage(player, "&b/is settings <设置项> <true|false> &f- 修改岛屿设置");
        MessageUtil.sendMessage(player, "&b/is setchunkbiome <生物群系> &f- 修改当前区块生物群系");
        MessageUtil.sendMessage(player, "&b/is setbiome <生物群系> &f- 修改整个岛屿生物群系");
        MessageUtil.sendMessage(player, "&b/is generator [维度] [矿石] [true/false/toggle] &f- 查看/控制刷石机矿石生成");
        MessageUtil.sendMessage(player, "&b/is upgrade [radius|generator] &f- 升级岛屿范围或刷石机等级");
        MessageUtil.sendMessage(player, "&b/is level &f- 扫描全岛并计算等级");
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
