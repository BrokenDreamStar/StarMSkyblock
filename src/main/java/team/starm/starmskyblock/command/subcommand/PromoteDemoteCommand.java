package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
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
 * 成员升/降级命令（/is promote|demote <玩家名>）
 * <p>
 * 通过 {@code args[0]} 区分 promote 与 demote 两种子动作，在同一处理器内复用权限校验与角色阶梯逻辑。
 * 角色阶梯：MEMBER -> MOD -> ADMIN，执行者只能调整比自己权限等级更低的成员，
 * 岛主不可被调整。需 SET_ROLE 权限。
 */
public class PromoteDemoteCommand extends SubCommand {

    public PromoteDemoteCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 执行 promote/demote 命令：校验权限与角色等级后调整目标成员角色并通知双方。
     */
    @Override
    public boolean execute(Player player, String[] args) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.SET_ROLE)) {
            MessageUtil.send(player, "team.role.no-permission");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.send(player, "team.role.usage", Map.of("subcommand", args[0]));
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
            MessageUtil.send(player, "team.remove.not-member");
            return true;
        }

        String targetName = getPlayerName(targetUuid);

        if (targetUuid.equals(player.getUniqueId())) {
            MessageUtil.send(player, "team.role.self");
            return true;
        }

        IslandPermissionLevel executorRole = island.getMemberRole(player.getUniqueId());
        IslandPermissionLevel targetRole = island.getMemberRole(targetUuid);

        if (executorRole.getPermissionLevel() <= targetRole.getPermissionLevel()) {
            MessageUtil.send(player, "team.role.higher-or-equal");
            return true;
        }

        IslandPermissionLevel newRole;
        boolean isPromote = args[0].equalsIgnoreCase("promote");

        if (isPromote) {
            switch (targetRole) {
                case MEMBER -> newRole = IslandPermissionLevel.MOD;
                case MOD -> newRole = IslandPermissionLevel.ADMIN;
                case ADMIN -> {
                    MessageUtil.send(player, "team.promote.max-reached");
                    return true;
                }
                default -> {
                    MessageUtil.send(player, "team.promote.invalid-target");
                    return true;
                }
            }
        } else {
            switch (targetRole) {
                case ADMIN -> newRole = IslandPermissionLevel.MOD;
                case MOD -> newRole = IslandPermissionLevel.MEMBER;
                case MEMBER -> {
                    MessageUtil.send(player, "team.demote.min-reached");
                    return true;
                }
                default -> {
                    MessageUtil.send(player, "team.demote.invalid-target");
                    return true;
                }
            }
        }

        if (plugin.getIslandManager().updateMemberRole(island.getId(), targetUuid, newRole)) {
            String successKey = isPromote ? "team.promote.success" : "team.demote.success";
            String notifyKey = isPromote ? "team.promote.target-notify" : "team.demote.target-notify";
            MessageUtil.send(player, successKey, Map.of("name", targetName, "role", newRole.getDisplayName()));
            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null) {
                MessageUtil.send(targetPlayer, notifyKey,
                        Map.of("player", player.getName(), "role", newRole.getDisplayName()));
            }
        } else {
            MessageUtil.send(player, "general.operation-failed");
        }
        return true;
    }

    /**
     * Tab 补全：第二参数补全比自己权限等级低的成员名。
     */
    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        if (args.length == 2) {
            var islandOpt = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
            if (islandOpt.isEmpty()) return List.of();
            Island island = islandOpt.get();
            IslandPermissionLevel executorRole = island.getMemberRole(player.getUniqueId());
            String prefix = args[1].toLowerCase();
            return island.getMembers().entrySet().stream()
                    .filter(e -> e.getValue().getPermissionLevel() < executorRole.getPermissionLevel())
                    .map(e -> {
                        var name = plugin.getPlayerRepo().getPlayerName(e.getKey());
                        return name.orElse(e.getKey().toString());
                    })
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
