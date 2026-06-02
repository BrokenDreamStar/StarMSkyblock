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
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        MessageUtil.sendMessage(player, "&a=== 岛屿成员列表 ===");

        String ownerName = getPlayerName(island.getOwnerId());
        boolean ownerOnline = Bukkit.getPlayer(island.getOwnerId()) != null;
        String ownerStatus = ownerOnline ? "" : " &7(离线)";
        MessageUtil.sendMessage(player, "&6岛主: &e" + ownerName + " &6("
                + IslandPermissionLevel.OWNER.getDisplayName() + ")" + ownerStatus);

        for (Map.Entry<UUID, IslandPermissionLevel> entry : island.getMembers().entrySet()) {
            String memberName = getPlayerName(entry.getKey());
            boolean memberOnline = Bukkit.getPlayer(entry.getKey()) != null;
            String status = memberOnline ? "" : " &7(离线)";
            MessageUtil.sendMessage(player, "&b成员: &e" + memberName +
                    " &b(" + entry.getValue().getDisplayName() + ")" + status);
        }

        if (island.getMembers().isEmpty()) {
            MessageUtil.sendMessage(player, "&7暂无其他成员");
        }
        return true;
    }

    private boolean showCoops(Player player) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        MessageUtil.sendMessage(player, "&a=== 合作者列表 ===");

        if (island.getCoops().isEmpty()) {
            MessageUtil.sendMessage(player, "&7暂无合作者");
            return true;
        }

        for (UUID coopUuid : island.getCoops()) {
            String coopName = getPlayerName(coopUuid);
            boolean coopOnline = Bukkit.getPlayer(coopUuid) != null;
            String status = coopOnline ? "" : " &7(离线)";
            MessageUtil.sendMessage(player, "&b合作者: &e" + coopName + status);
        }
        return true;
    }

    private boolean showMyCoops(Player player) {
        IslandManager islandManager = plugin.getIslandManager();
        List<Island> coopIslands = islandManager.getIslandsByCoop(player.getUniqueId());
        if (coopIslands.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你不是任何岛屿的合作者！");
            return true;
        }

        MessageUtil.sendMessage(player, "&a=== 我的合作者身份 ===");
        for (Island coopIsland : coopIslands) {
            String ownerName = getPlayerName(coopIsland.getOwnerId());
            boolean ownerOnline = Bukkit.getPlayer(coopIsland.getOwnerId()) != null;
            String status = ownerOnline ? "" : " &7(离线)";
            MessageUtil.sendMessage(player, "&b岛屿 #" + coopIsland.getId() + " &e(岛主: " + ownerName + ")" + status);
        }
        return true;
    }

    private boolean showMyPerms(Player player) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        IslandPermissionLevel playerRole = island.getMemberRole(player.getUniqueId());
        MessageUtil.sendMessage(player, "&a=== 我的岛屿权限 ===");
        MessageUtil.sendMessage(player, "&7当前权限组: &e" + playerRole.getDisplayName()
                + " &7(等级 " + playerRole.getPermissionLevel() + ")");
        MessageUtil.sendMessage(player, "");

        for (IslandPermission perm : IslandPermission.values()) {
            boolean hasPerm = island.hasPermission(player.getUniqueId(), perm);
            String icon = hasPerm ? "&a✔" : "&c✘";
            MessageUtil.sendMessage(player, icon + " &7" + perm.getDisplayName());
        }
        return true;
    }

    private boolean showRole(Player player) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        IslandPermissionLevel playerRole = island.getMemberRole(player.getUniqueId());
        MessageUtil.sendMessage(player, "&a你的岛屿权限组: &e" + playerRole.getDisplayName());
        return true;
    }
}
