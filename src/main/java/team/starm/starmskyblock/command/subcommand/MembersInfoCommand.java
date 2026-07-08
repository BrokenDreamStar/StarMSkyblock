package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MembersInfoCommand extends SubCommand {

    public MembersInfoCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 1, "/is " + args[0])) return true;

        return switch (args[0].toLowerCase()) {
            case "members" -> showMembers(player);
            case "coops" -> showCoops(player);
            case "mycoops" -> showMyCoops(player);
            case "myperms" -> showMyPerms(player);
            case "role" -> showRole(player);
            default -> false;
        };
    }

    private boolean showMembers(Player player) {
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

    private boolean showCoops(Player player) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();
        MessageUtil.send(player, "members.coop.header");

        if (island.getCoops().isEmpty()) {
            MessageUtil.send(player, "members.coop.empty");
            return true;
        }

        for (UUID coopUuid : island.getCoops()) {
            String coopName = getPlayerName(coopUuid);
            boolean coopOnline = Bukkit.getPlayer(coopUuid) != null;
            String status = coopOnline ? "" : " &7(离线)";
            MessageUtil.send(player, "members.coop.entry", Map.of("name", coopName, "status", status));
        }
        return true;
    }

    private boolean showMyCoops(Player player) {
        IslandManager islandManager = plugin.getIslandManager();
        List<Island> coopIslands = islandManager.getIslandsByCoop(player.getUniqueId());
        if (coopIslands.isEmpty()) {
            MessageUtil.send(player, "members.mycoops.empty");
            return true;
        }

        MessageUtil.send(player, "members.mycoops.header");
        for (Island coopIsland : coopIslands) {
            String ownerName = getPlayerName(coopIsland.getOwnerId());
            boolean ownerOnline = Bukkit.getPlayer(coopIsland.getOwnerId()) != null;
            String status = ownerOnline ? "" : " &7(离线)";
            MessageUtil.send(player, "members.mycoops.entry",
                    Map.of("id", coopIsland.getId(), "name", ownerName, "status", status));
        }
        return true;
    }

    private boolean showMyPerms(Player player) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();
        IslandPermissionLevel playerRole = island.getMemberRole(player.getUniqueId());
        MessageUtil.send(player, "members.perms.header");
        MessageUtil.send(player, "members.perms.current",
                Map.of("role", playerRole.getDisplayName(), "level", playerRole.getPermissionLevel()));
        MessageUtil.send(player, "general.empty-line");

        for (IslandPermission perm : IslandPermission.values()) {
            boolean hasPerm = island.hasPermission(player.getUniqueId(), perm);
            if (hasPerm) {
                MessageUtil.send(player, "members.perms.perm-enabled", Map.of("name", perm.getDisplayName()));
            } else {
                MessageUtil.send(player, "members.perms.perm-disabled", Map.of("name", perm.getDisplayName()));
            }
        }
        return true;
    }

    private boolean showRole(Player player) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();
        IslandPermissionLevel playerRole = island.getMemberRole(player.getUniqueId());
        MessageUtil.send(player, "members.role.display", Map.of("role", playerRole.getDisplayName()));
        return true;
    }
}
