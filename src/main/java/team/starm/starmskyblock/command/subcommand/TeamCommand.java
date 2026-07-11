package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.InvitationManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import team.starm.starmskyblock.StarMSkyblock;

/**
 * 团队管理命令（/is team list|invite|remove|accept|decline）
 * <p>
 * 管理岛屿正式成员的邀请、加入、退出流程。邀请通过 {@link InvitationManager} 走待处理队列，
 * 玩家可 accept/decline。移除成员需 REMOVE_MEMBER 权限并追加 confirm 确认。
 */
public class TeamCommand extends SubCommand {

    public TeamCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 执行 /is team 命令：按子参数分发到对应处理器。
     */
    @Override
    public boolean execute(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(player, "team.usage.header");
            MessageUtil.send(player, "team.usage.list");
            MessageUtil.send(player, "team.usage.invite");
            MessageUtil.send(player, "team.usage.remove");
            MessageUtil.send(player, "team.usage.accept");
            MessageUtil.send(player, "team.usage.decline");
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "list" -> handleList(player);
            case "invite" -> handleInvite(player, args);
            case "remove" -> handleRemove(player, args);
            case "accept" -> handleAccept(player);
            case "decline" -> handleDecline(player);
            default -> {
                MessageUtil.send(player, "team.unknown-subcommand", Map.of("subcommand", args[1]));
                MessageUtil.send(player, "team.usage.summary");
                yield true;
            }
        };
    }

    /** 展示本岛屿岛主及全部成员名单（含在线/离线状态）。 */
    private boolean handleList(Player player) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();
        MessageUtil.send(player, "team.list.header");

        String ownerName = getPlayerName(island.getOwnerId());
        boolean ownerOnline = Bukkit.getPlayer(island.getOwnerId()) != null;
        String ownerStatus = ownerOnline ? "" : " &7(离线)";
        MessageUtil.send(player, "team.list.owner", Map.of(
                "name", ownerName,
                "role", IslandPermissionLevel.OWNER.getDisplayName(),
                "status", ownerStatus));

        for (Map.Entry<UUID, IslandPermissionLevel> entry : island.getMembers().entrySet()) {
            String memberName = getPlayerName(entry.getKey());
            boolean memberOnline = Bukkit.getPlayer(entry.getKey()) != null;
            String status = memberOnline ? "" : " &7(离线)";
            MessageUtil.send(player, "team.list.member", Map.of(
                    "name", memberName,
                    "role", entry.getValue().getDisplayName(),
                    "status", status));
        }

        if (island.getMembers().isEmpty()) {
            MessageUtil.send(player, "team.list.no-members");
        }
        return true;
    }

    /** 处理 /is team invite：向目标在线玩家发送入岛邀请（需 INVITE_MEMBER 权限）。 */
    private boolean handleInvite(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 3, "/is team invite <玩家名>")) return true;

        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.INVITE_MEMBER)) {
            MessageUtil.send(player, "team.invite.no-permission");
            return true;
        }

        if (args.length < 3) {
            MessageUtil.send(player, "team.invite.usage");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[2]);
        if (targetPlayer == null) {
            MessageUtil.send(player, "general.player-not-found");
            return true;
        }

        if (targetPlayer.getUniqueId().equals(player.getUniqueId())) {
            MessageUtil.send(player, "team.invite.self");
            return true;
        }

        InvitationManager invitationManager = plugin.getInvitationManager();
        if (invitationManager.sendInvitation(player.getUniqueId(), targetPlayer.getUniqueId(), island.getId())) {
            MessageUtil.send(player, "team.invite.waiting");
        } else {
            MessageUtil.send(player, "team.invite.failed");
        }
        return true;
    }

    /** 处理 /is team accept：接受当前待处理邀请加入邀请方岛屿。 */
    private boolean handleAccept(Player player) {
        var invitationManager = plugin.getInvitationManager();

        if (!invitationManager.hasPendingInvitation(player.getUniqueId())) {
            MessageUtil.send(player, "team.invite.no-pending");
            return true;
        }

        if (invitationManager.acceptInvitation(player.getUniqueId())) {
            MessageUtil.send(player, "team.accept.success");
        } else {
            MessageUtil.send(player, "team.accept.failed");
        }
        return true;
    }

    /** 处理 /is team decline：拒绝当前待处理邀请。 */
    private boolean handleDecline(Player player) {
        var invitationManager = plugin.getInvitationManager();

        if (!invitationManager.hasPendingInvitation(player.getUniqueId())) {
            MessageUtil.send(player, "team.invite.no-pending");
            return true;
        }

        if (invitationManager.declineInvitation(player.getUniqueId())) {
            MessageUtil.send(player, "team.decline.success");
        } else {
            MessageUtil.send(player, "team.decline.failed");
        }
        return true;
    }

    /** 处理 /is team remove：移除指定成员（需 REMOVE_MEMBER 权限与 confirm 确认）。 */
    private boolean handleRemove(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 4, "/is team remove <玩家名> [confirm]")) return true;

        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.REMOVE_MEMBER)) {
            MessageUtil.send(player, "team.remove.no-permission");
            return true;
        }

        if (args.length < 3) {
            MessageUtil.send(player, "team.remove.usage");
            return true;
        }

        UUID targetUuid = island.getMembers().keySet().stream()
                .filter(uuid -> {
                    String name = getPlayerName(uuid);
                    return name != null && name.equalsIgnoreCase(args[2]);
                })
                .findFirst().orElse(null);
        if (targetUuid == null) {
            MessageUtil.send(player, "team.remove.not-member");
            return true;
        }

        String targetName = getPlayerName(targetUuid);

        if (targetUuid.equals(island.getOwnerId())) {
            MessageUtil.send(player, "general.cannot-remove-owner");
            return true;
        }

        if (args.length < 4 || !args[3].equalsIgnoreCase("confirm")) {
            MessageUtil.send(player, "team.remove.confirm", Map.of("name", targetName));
            return true;
        }

        if (plugin.getIslandManager().removeMemberFromIsland(island.getId(), targetUuid)) {
            MessageUtil.send(player, "team.remove.success", Map.of("name", targetName));
            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null) {
                MessageUtil.send(targetPlayer, "team.remove.removed", Map.of("player", player.getName()));
            }
        } else {
            MessageUtil.send(player, "team.remove.failed");
        }
        return true;
    }

    /**
     * Tab 补全：第二参数补全子命令；invite 补全可邀请的在线访客，remove 补全可移除的低权限成员。
     */
    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return List.of("list", "invite", "remove", "accept", "decline").stream()
                    .filter(v -> v.startsWith(prefix))
                    .toList();
        }
        if (args.length == 3) {
            String sub = args[1].toLowerCase();
            var islandOpt = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
            if (islandOpt.isEmpty()) return List.of();
            Island island = islandOpt.get();
            String prefix = args[2].toLowerCase();
            if (sub.equals("invite")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> {
                            Player p = Bukkit.getPlayer(name);
                            if (p == null || p.getUniqueId().equals(player.getUniqueId())) return false;
                            return island.getMemberRole(p.getUniqueId()) == IslandPermissionLevel.VISITOR;
                        })
                        .filter(name -> name.toLowerCase().startsWith(prefix))
                        .toList();
            }
            if (sub.equals("remove")) {
                IslandPermissionLevel executorRole = island.getMemberRole(player.getUniqueId());
                return island.getMembers().entrySet().stream()
                        .filter(e -> e.getValue().getPermissionLevel() < executorRole.getPermissionLevel())
                        .map(e -> {
                            var name = plugin.getPlayerRepo().getPlayerName(e.getKey());
                            return name.orElse(e.getKey().toString());
                        })
                        .filter(name -> name.toLowerCase().startsWith(prefix))
                        .toList();
            }
        }
        return List.of();
    }
}
