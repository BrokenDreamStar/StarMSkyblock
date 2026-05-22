package team.starm.starmskyblock.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
import team.starm.starmskyblock.util.ColorUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 管理员命令处理器 — /isadmin。
 * 提供服务器管理员对岛屿的后台管理功能。
 * 当前支持：setradius（修改岛屿半径）。
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
            ColorUtil.setSilent(player.getUniqueId(), true);
        }

        try {
            return handleCommand(sender, args);
        } finally {
            if (silent) {
                ColorUtil.setSilent(player.getUniqueId(), false);
            }
        }
    }

    /**
     * 命令路由。目前仅有 setradius 子命令，未来可在此扩展。
     */
    private boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, "&c用法: /isadmin setradius <岛主ID> <新半径>");
            return true;
        }

        if (args[0].equalsIgnoreCase("setradius")) {
            String ownerName = args[1];
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
            Optional<Island> optionalIsland = islandManager.getIslandByPlayerName(ownerName);

            if (optionalIsland.isEmpty()) {
                MessageUtil.sendMessage(sender, "&c找不到岛主为 " + ownerName + " 的岛屿！");
                return true;
            }

            Island island = optionalIsland.get();
            islandManager.updateIslandRadius(island.getId(), newRadius);
            MessageUtil.sendMessage(sender, "&a成功将 &e" + ownerName + " &a的岛屿半径修改为 &e" + newRadius + " &a区块。");
            return true;
        }

        MessageUtil.sendMessage(sender, "&c未知子命令，用法: /isadmin setradius <岛主ID> <新半径>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, @NonNull Command command, @NonNull String alias, String[] args) {
        if (!sender.hasPermission("skyblock.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            if ("setradius".startsWith(prefix)) {
                return Collections.singletonList("setradius");
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("setradius")) {
            String prefix = args[1].toLowerCase();
            List<String> playerNames = new ArrayList<>();
            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                if (offlinePlayer.getName() != null && offlinePlayer.getName().toLowerCase().startsWith(prefix)) {
                    playerNames.add(offlinePlayer.getName());
                }
            }
            return playerNames;
        }
        return new ArrayList<>();
    }
}
