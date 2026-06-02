package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InfoCommand extends SubCommand {

    public InfoCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 1, "/is info")) return true;

        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();

        MessageUtil.sendMessage(player, "&a=== 岛屿信息 ===");

        String islandName = island.getName();
        if (islandName == null || islandName.isBlank()) {
            islandName = "岛屿 #" + island.getId();
        }
        MessageUtil.sendMessage(player, "&b名称: &e" + islandName);
        MessageUtil.sendMessage(player, "&bID: &e" + island.getId());
        MessageUtil.sendMessage(player, "&b等级: &e" + island.getLevel());
        MessageUtil.sendMessage(player, "&b已解锁大小: &e" + island.getRadius() + " &7(半径) / &e" + (island.getRadius() * 2 + 1) + "×" + (island.getRadius() * 2 + 1) + " &7(区块)");

        MessageUtil.sendMessage(player, "&a--- 岛屿成员 ---");

        Map<IslandPermissionLevel, List<String>> groups = new LinkedHashMap<>();
        groups.put(IslandPermissionLevel.OWNER, new ArrayList<>());
        groups.put(IslandPermissionLevel.ADMIN, new ArrayList<>());
        groups.put(IslandPermissionLevel.MOD, new ArrayList<>());
        groups.put(IslandPermissionLevel.MEMBER, new ArrayList<>());

        for (Map.Entry<UUID, IslandPermissionLevel> entry : island.getMembers().entrySet()) {
            List<String> list = groups.get(entry.getValue());
            if (list != null) {
                list.add(getPlayerNameWithFallback(entry.getKey()));
            }
        }

        String ownerName = getPlayerNameWithFallback(island.getOwnerId());
        groups.get(IslandPermissionLevel.OWNER).add(ownerName);

        for (Map.Entry<IslandPermissionLevel, List<String>> entry : groups.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            String color = entry.getKey().getColor();
            String roleName = entry.getKey().getDisplayName();
            for (String name : entry.getValue()) {
                MessageUtil.sendMessage(player, "&f - " + color + name + "(" + color + roleName + ")");
            }
        }

        if (island.getCreatedAt() != null && !island.getCreatedAt().isBlank()) {
            MessageUtil.sendMessage(player, "&b创建时间: &e" + island.getCreatedAt());
        }

        return true;
    }

    private String getPlayerNameWithFallback(UUID uuid) {
        Optional<String> dbName = plugin.getPlayerRepo().getPlayerName(uuid);
        if (dbName.isPresent()) return dbName.get();
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        String name = offlinePlayer.getName();
        return name != null ? name : "未知";
    }
}
