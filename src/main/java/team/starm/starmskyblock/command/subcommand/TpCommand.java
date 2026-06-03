package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;

import team.starm.starmskyblock.setting.IslandSetting;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.List;
import java.util.Optional;

public class TpCommand extends SubCommand {

    public TpCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&c用法: /is tp <岛屿名称> [岛屿ID] [confirm]");
            return true;
        }

        if (!assertMaxArgs(player, args, 4, "/is tp <岛屿名称> [岛屿ID] [confirm]")) return true;

        var islandManager = plugin.getIslandManager();
        String islandName = args[1].replace('§', '&');
        List<Island> matchingIslands = islandManager.getIslandsByName(islandName);

        if (matchingIslands.isEmpty()) {
            if (args.length >= 3) {
                try {
                    int fallbackId = Integer.parseInt(args[2]);
                    Optional<Island> byId = islandManager.getIsland(fallbackId);
                    if (byId.isPresent()) {
                        matchingIslands = List.of(byId.get());
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (matchingIslands.isEmpty()) {
                MessageUtil.sendMessage(player, "&c未找到名称为 &e" + islandName + " &c的岛屿！");
                return true;
            }
        }

        Island targetIsland;
        int confirmArgIndex;

        if (matchingIslands.size() > 1) {
            if (args.length < 3 || args[2].equalsIgnoreCase("confirm")) {
                showIslandList(player, matchingIslands);
                return true;
            }
            try {
                int islandId = Integer.parseInt(args[2]);
                Optional<Island> matched = matchingIslands.stream()
                        .filter(i -> i.getId() == islandId)
                        .findFirst();
                if (matched.isEmpty()) {
                    MessageUtil.sendMessage(player, "&c未找到ID为 &e" + islandId + " &c的匹配岛屿！");
                    showIslandList(player, matchingIslands);
                    return true;
                }
                targetIsland = matched.get();
                confirmArgIndex = 3;
            } catch (NumberFormatException e) {
                showIslandList(player, matchingIslands);
                return true;
            }
        } else {
            targetIsland = matchingIslands.getFirst();
            confirmArgIndex = 2;
        }

        if (!targetIsland.getSetting(IslandSetting.TP)) {
            MessageUtil.sendMessage(player, "&c该岛屿未开放传送！");
            return true;
        }

        ConfigManager config = plugin.getConfigManager();
        var worldManager = plugin.getWorldManager();
        Location spawnLocation;
        World targetWorld;

        if (targetIsland.hasCustomHome()) {
            Island.WorldType worldType = targetIsland.getCustomHomeWorldType();
            targetWorld = switch (worldType) {
                case NETHER -> worldManager.getSkyblockNether();
                case END -> worldManager.getSkyblockEnd();
                default -> worldManager.getSkyblockWorld();
            };
            spawnLocation = new Location(targetWorld,
                    targetIsland.getCustomHomeX(), targetIsland.getCustomHomeY(), targetIsland.getCustomHomeZ());
        } else {
            targetWorld = worldManager.getSkyblockWorld();
            double[] offsets = config.getTeleportOffsetsBySchematicAndWorldType(
                    targetIsland.getSchematicId(), Island.WorldType.NORMAL);
            double teleportX = (targetIsland.getCenterChunkX() * 16) + 8 + offsets[0];
            double teleportY = config.getIslandHeight() + offsets[1];
            double teleportZ = (targetIsland.getCenterChunkZ() * 16) + 8 + offsets[2];
            spawnLocation = new Location(targetWorld, teleportX, teleportY, teleportZ);
        }

        boolean confirmed = args.length > confirmArgIndex && args[confirmArgIndex].equalsIgnoreCase("confirm");
        if (!isLocationSafe(spawnLocation) && !confirmed) {
            MessageUtil.sendMessage(player, "&c警告：该岛屿传送点不安全！");
            MessageUtil.sendMessage(player, "&c使用 &e/is tp " + islandName + " confirm &c强制传送");
            return true;
        }

        int countdown = plugin.getConfigManager().getTeleportCountdown();
        if (countdown > 0) {
            plugin.getTeleportCountdownListener().startCountdown(player, spawnLocation, countdown,
                    "&a已传送到&r" + targetIsland.getName());
        } else {
            player.teleport(spawnLocation);
            MessageUtil.sendMessage(player, "&a已传送到&r" + targetIsland.getName());
        }
        return true;
    }

    private void showIslandList(Player player, List<Island> islands) {
        MessageUtil.sendMessage(player, "&a找到多个同名岛屿：");
        for (Island island : islands) {
            String ownerName = getPlayerName(island.getOwnerId());
            MessageUtil.sendMessage(player, "  &e#" + island.getId() + " &7- &f" + ownerName);
        }
        MessageUtil.sendMessage(player, "&7使用 &e/is tp <名称> <ID> &7指定具体岛屿");
    }
}
