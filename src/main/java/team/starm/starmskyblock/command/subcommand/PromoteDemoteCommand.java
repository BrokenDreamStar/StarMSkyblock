package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Optional;
import java.util.UUID;

public class PromoteDemoteCommand extends SubCommand {

    public PromoteDemoteCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.SET_ROLE)) {
            MessageUtil.sendMessage(player, "&c你没有权限管理成员权限组！");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&c用法: /is " + args[0] + " <玩家名>");
            return true;
        }

        if (!assertMaxArgs(player, args, 2, "/is " + args[0] + " <玩家名>")) return true;

        UUID targetUuid = island.getMembers().keySet().stream()
                .filter(uuid -> {
                    String name = getPlayerName(uuid);
                    return name != null && name.equalsIgnoreCase(args[1]);
                })
                .findFirst().orElse(null);
        if (targetUuid == null) {
            MessageUtil.sendMessage(player, "&c该玩家不是岛屿成员！");
            return true;
        }

        String targetName = getPlayerName(targetUuid);

        if (targetUuid.equals(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&c你不能对自己使用此命令！");
            return true;
        }

        IslandPermissionLevel executorRole = island.getMemberRole(player.getUniqueId());
        IslandPermissionLevel targetRole = island.getMemberRole(targetUuid);

        if (executorRole.getPermissionLevel() <= targetRole.getPermissionLevel()) {
            MessageUtil.sendMessage(player, "&c你无法管理同权限或更高权限的成员！");
            return true;
        }

        IslandPermissionLevel newRole;
        boolean isPromote = args[0].equalsIgnoreCase("promote");

        if (isPromote) {
            switch (targetRole) {
                case MEMBER -> newRole = IslandPermissionLevel.MOD;
                case MOD -> newRole = IslandPermissionLevel.ADMIN;
                case ADMIN -> {
                    MessageUtil.sendMessage(player, "&c该玩家已经是最高权限组！");
                    return true;
                }
                default -> {
                    MessageUtil.sendMessage(player, "&c只能晋升岛员、风纪委员、管理员！");
                    return true;
                }
            }
        } else {
            switch (targetRole) {
                case ADMIN -> newRole = IslandPermissionLevel.MOD;
                case MOD -> newRole = IslandPermissionLevel.MEMBER;
                case MEMBER -> {
                    MessageUtil.sendMessage(player, "&c该玩家已经是最低权限组！");
                    return true;
                }
                default -> {
                    MessageUtil.sendMessage(player, "&c只能降级岛员、风纪委员、管理员！");
                    return true;
                }
            }
        }

        if (plugin.getIslandManager().updateMemberRole(island.getId(), targetUuid, newRole)) {
            String action = isPromote ? "晋升" : "降级";
            MessageUtil.sendMessage(player, "&a成功" + action + " &e" + targetName + " &a为 &e" + newRole.getDisplayName());
            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null) {
                MessageUtil.sendMessage(targetPlayer,
                        "&a你的岛屿权限组已被 &e" + player.getName() + " &a" + action + "为 &e" + newRole.getDisplayName());
            }
        } else {
            MessageUtil.sendMessage(player, "&c操作失败，请稍后重试。");
        }
        return true;
    }
}
