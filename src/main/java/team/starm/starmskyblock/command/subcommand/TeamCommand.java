package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.InvitationManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Optional;
import java.util.UUID;

public class TeamCommand extends SubCommand {

    public TeamCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&c用法:");
            MessageUtil.sendMessage(player, "&b/is team invite <玩家> &f- 邀请玩家加入岛屿");
            MessageUtil.sendMessage(player, "&b/is team remove <玩家> [confirm] &f- 踢出岛屿成员");
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "invite" -> handleInvite(player, args);
            case "remove" -> handleRemove(player, args);
            default -> {
                MessageUtil.sendMessage(player, "&c未知的子命令: &e" + args[1]);
                MessageUtil.sendMessage(player, "&c用法: /is team invite|remove <玩家名>");
                yield true;
            }
        };
    }

    private boolean handleInvite(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 3, "/is team invite <玩家名>")) return true;

        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.INVITE_MEMBER)) {
            MessageUtil.sendMessage(player, "&c你没有权限邀请成员！");
            return true;
        }

        if (args.length < 3) {
            MessageUtil.sendMessage(player, "&c用法: /is team invite <玩家名>");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[2]);
        if (targetPlayer == null) {
            MessageUtil.sendMessage(player, "&c玩家不存在或不在线！");
            return true;
        }

        if (targetPlayer.getUniqueId().equals(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&c你不能邀请自己！");
            return true;
        }

        InvitationManager invitationManager = plugin.getInvitationManager();
        if (invitationManager.sendInvitation(player.getUniqueId(), targetPlayer.getUniqueId(), island.getId())) {
            MessageUtil.sendMessage(player, "&7等待对方确认...");
        } else {
            MessageUtil.sendMessage(player, "&c邀请失败！该玩家可能已有岛屿或已有待处理的邀请。");
        }
        return true;
    }

    private boolean handleRemove(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 4, "/is team remove <玩家名> [confirm]")) return true;

        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.REMOVE_MEMBER)) {
            MessageUtil.sendMessage(player, "&c你没有权限踢出成员！");
            return true;
        }

        if (args.length < 3) {
            MessageUtil.sendMessage(player, "&c用法: /is team remove <玩家名> [confirm]");
            return true;
        }

        UUID targetUuid = island.getMembers().keySet().stream()
                .filter(uuid -> {
                    String name = getPlayerName(uuid);
                    return name != null && name.equalsIgnoreCase(args[2]);
                })
                .findFirst().orElse(null);
        if (targetUuid == null) {
            MessageUtil.sendMessage(player, "&c该玩家不是岛屿成员！");
            return true;
        }

        String targetName = getPlayerName(targetUuid);

        if (targetUuid.equals(island.getOwnerId())) {
            MessageUtil.sendMessage(player, "&c你不能踢出岛主！");
            return true;
        }

        if (args.length < 4 || !args[3].equalsIgnoreCase("confirm")) {
            MessageUtil.sendMessage(player, "&c警告：将踢出 &e" + targetName + " &c，使用 &e/is team remove " + targetName + " confirm &c确认。");
            return true;
        }

        if (plugin.getIslandManager().removeMemberFromIsland(island.getId(), targetUuid)) {
            MessageUtil.sendMessage(player, "&a成功踢出 &e" + targetName + " &a从岛屿");
            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null) {
                MessageUtil.sendMessage(targetPlayer, "&c你已被 &e" + player.getName() + " &c从岛屿踢出");
            }
        } else {
            MessageUtil.sendMessage(player, "&c踢出失败，该玩家可能不是岛屿成员。");
        }
        return true;
    }
}
