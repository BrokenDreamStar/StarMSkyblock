package team.starm.starmskyblock.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.message.MessageUtil;

import team.starm.starmskyblock.config.GeneratorConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 管理员命令处理器 — /isadmin。
 * 提供服务器管理员对岛屿的后台管理功能。
 * 当前支持：setradius（修改岛屿半径）、setgenerator（设置刷石机等级）。
 * 需要 skyblock.admin 权限。
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    private final StarMSkyblock plugin;

    public AdminCommand(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, "&c只有玩家才能执行该命令！");
            return true;
        }

        // 提取 -s 静默标记
        boolean silent = args.length > 0 && (args[args.length - 1].equals("-s"));
        if (silent) {
            args = java.util.Arrays.copyOf(args, args.length - 1);
            MessageUtil.setSilent(player.getUniqueId(), true);
        }

        try {
            return handleCommand(sender, args);
        } finally {
            if (silent) {
                MessageUtil.setSilent(player.getUniqueId(), false);
            }
        }
    }

    /**
     * 命令路由。当前支持：setradius、setgenerator。
     */
    private boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        if (subCmd.equals("setradius")) {
            return handleSetRadius(sender, args);
        } else if (subCmd.equals("setgenerator")) {
            return handleSetGenerator(sender, args);
        }

        sendUsage(sender);
        return true;
    }

    private void sendUsage(CommandSender sender) {
        MessageUtil.sendMessage(sender, "&c用法:");
        MessageUtil.sendMessage(sender, "&c  /isadmin setradius <岛屿ID> <新半径>");
        MessageUtil.sendMessage(sender, "&c  /isadmin setgenerator <岛屿ID> <等级>");
    }

    private boolean handleSetRadius(CommandSender sender, String[] args) {
        if (args.length != 3) {
            MessageUtil.sendMessage(sender, "&c用法: /isadmin setradius <岛屿ID> <新半径>");
            return true;
        }

        int islandId;
        try {
            islandId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "&c岛屿ID必须是整数！");
            return true;
        }

        int newRadius;
        try {
            newRadius = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "&c新半径必须是整数！");
            return true;
        }

        int maxRadius = plugin.getConfigManager().getIslandMaxRadius();
        if (newRadius > maxRadius) {
            MessageUtil.sendMessage(sender, "&c新的半径不能超过配置文件中的最大半径限制: " + maxRadius);
            return true;
        }

        if (newRadius <= 0) {
            MessageUtil.sendMessage(sender, "&c半径必须大于0！");
            return true;
        }

        IslandManager islandManager = plugin.getIslandManager();
        Optional<Island> optionalIsland = islandManager.getIsland(islandId);

        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(sender, "&c找不到ID为 " + islandId + " 的岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        islandManager.updateIslandRadius(island.getId(), newRadius);
        MessageUtil.sendMessage(sender, "&a成功将岛屿 &e#" + islandId + " &a的半径修改为 &e" + newRadius + " &a区块。");
        return true;
    }

    private boolean handleSetGenerator(CommandSender sender, String[] args) {
        if (args.length != 3) {
            MessageUtil.sendMessage(sender, "&c用法: /isadmin setgenerator <岛屿ID> <等级>");
            return true;
        }

        int islandId;
        try {
            islandId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "&c岛屿ID必须是整数！");
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "&c刷石机等级必须是整数！");
            return true;
        }

        GeneratorConfigManager genConfig = plugin.getGeneratorConfigManager();
        if (!genConfig.isEnabled()) {
            MessageUtil.sendMessage(sender, "&c刷石机功能未启用！");
            return true;
        }

        int maxLevel = genConfig.getMaxLevel();
        if (level < 1 || level > maxLevel) {
            MessageUtil.sendMessage(sender, "&c等级必须在 1 ~ " + maxLevel + " 之间！");
            return true;
        }

        IslandManager islandManager = plugin.getIslandManager();
        Optional<Island> optionalIsland = islandManager.getIsland(islandId);

        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(sender, "&c找不到ID为 " + islandId + " 的岛屿！");
            return true;
        }

        islandManager.updateIslandGeneratorLevel(islandId, level);
        MessageUtil.sendMessage(sender, "&a成功将岛屿 &e#" + islandId + " &a的刷石机等级设置为 &e" + level);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, @NonNull Command command, @NonNull String alias, String[] args) {
        if (!sender.hasPermission("skyblock.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            if ("setradius".startsWith(prefix)) {
                matches.add("setradius");
            }
            if ("setgenerator".startsWith(prefix)) {
                matches.add("setgenerator");
            }
            return matches;
        }

        return new ArrayList<>();
    }
}
