package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Optional;
import java.util.UUID;

public class CoopCommand extends SubCommand {

    public CoopCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&c用法:");
            MessageUtil.sendMessage(player, "&b/is coop add <玩家> &f- 添加合作者");
            MessageUtil.sendMessage(player, "&b/is coop remove <玩家> &f- 移除合作者");
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "add" -> handleAdd(player, args);
            case "remove" -> handleRemove(player, args);
            default -> {
                MessageUtil.sendMessage(player, "&c未知的子命令: &e" + args[1]);
                MessageUtil.sendMessage(player, "&c用法: /is coop add|remove <玩家名>");
                yield true;
            }
        };
    }

    private boolean handleAdd(Player player, String[] args) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.INVITE_COOP)) {
            MessageUtil.sendMessage(player, "&c你没有权限邀请合作者！");
            return true;
        }

        if (args.length < 3) {
            MessageUtil.sendMessage(player, "&c用法: /is coop add <玩家名>");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[2]);
        if (targetPlayer == null) {
            MessageUtil.sendMessage(player, "&c玩家不存在或不在线！");
            return true;
        }

        if (targetPlayer.getUniqueId().equals(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&c你不能将自己添加为合作者！");
            return true;
        }

        var islandManager = plugin.getIslandManager();
        if (islandManager.getIslandByPlayer(targetPlayer.getUniqueId()).isEmpty()) {
            MessageUtil.sendMessage(player, "&c该玩家没有岛屿，无法添加为合作者！");
            return true;
        }

        if (island.isCoop(targetPlayer.getUniqueId())) {
            MessageUtil.sendMessage(player, "&c该玩家已经是合作者！");
            return true;
        }

        if (island.getMembers().containsKey(targetPlayer.getUniqueId())) {
            MessageUtil.sendMessage(player, "&c该玩家已经是岛屿成员，无法添加为合作者！");
            return true;
        }

        if (islandManager.addCoopToIsland(island.getId(), targetPlayer.getUniqueId())) {
            MessageUtil.sendMessage(player, "&a已将 &e" + targetPlayer.getName() + " &a添加为合作者！");
            MessageUtil.sendMessage(targetPlayer, "&a你已被 &e" + player.getName() + " &a添加为岛屿合作者！");
        } else {
            MessageUtil.sendMessage(player, "&c操作失败，请稍后重试。");
        }
        return true;
    }

    private boolean handleRemove(Player player, String[] args) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.REMOVE_COOP)) {
            MessageUtil.sendMessage(player, "&c你没有权限移除合作者！");
            return true;
        }

        if (args.length < 3) {
            MessageUtil.sendMessage(player, "&c用法: /is coop remove <玩家名>");
            return true;
        }

        UUID targetUuid = island.getCoops().stream()
                .filter(uuid -> {
                    String name = getPlayerName(uuid);
                    return name != null && name.equalsIgnoreCase(args[2]);
                })
                .findFirst().orElse(null);
        if (targetUuid == null) {
            MessageUtil.sendMessage(player, "&c该玩家不是合作者！");
            return true;
        }

        String targetName = getPlayerName(targetUuid);

        if (targetUuid.equals(island.getOwnerId())) {
            MessageUtil.sendMessage(player, "&c你不能移除岛主！");
            return true;
        }

        if (plugin.getIslandManager().removeCoopFromIsland(island.getId(), targetUuid)) {
            MessageUtil.sendMessage(player, "&a已移除合作者 &e" + targetName);
            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null) {
                MessageUtil.sendMessage(targetPlayer, "&c你已被 &e" + player.getName() + " &c移出岛屿合作者队伍");
            }
        } else {
            MessageUtil.sendMessage(player, "&c操作失败，请稍后重试。");
        }
        return true;
    }
}
