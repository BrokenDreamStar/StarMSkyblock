package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;

import team.starm.starmskyblock.setting.IslandSetting;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class TpCommand extends SubCommand {

    public TpCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(player, "tp.usage");
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
                MessageUtil.send(player, "tp.not-found", Map.of("name", islandName));
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
                    MessageUtil.send(player, "tp.id-not-found", Map.of("id", islandId));
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
            MessageUtil.send(player, "tp.disabled");
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
                    targetIsland.getCustomHomeX(), targetIsland.getCustomHomeY(), targetIsland.getCustomHomeZ(),
                    targetIsland.getCustomHomeYaw(), targetIsland.getCustomHomePitch());
        } else {
            targetWorld = worldManager.getSkyblockWorld();
            double[] offsets = config.getTeleportOffsetsBySchematicAndWorldType(
                    targetIsland.getSchematicId(), Island.WorldType.NORMAL);
            double teleportX = (targetIsland.getCenterChunkX() * 16) + 8 + offsets[0];
            double teleportY = config.getIslandHeight() + offsets[1];
            double teleportZ = (targetIsland.getCenterChunkZ() * 16) + 8 + offsets[2];
            spawnLocation = new Location(targetWorld, teleportX, teleportY, teleportZ,
                    (float) offsets[3], (float) offsets[4]);
        }

        boolean confirmed = args.length > confirmArgIndex && args[confirmArgIndex].equalsIgnoreCase("confirm");
        if (!isLocationSafe(spawnLocation) && !confirmed) {
            MessageUtil.send(player, "tp.unsafe-warning");
            MessageUtil.send(player, "tp.force-hint", Map.of("name", islandName));
            return true;
        }

        int countdown = plugin.getConfigManager().getTeleportCountdown();
        if (countdown > 0) {
            plugin.getTeleportCountdownListener().startCountdown(player, spawnLocation, countdown,
                    MessageUtil.format("tp.success", Map.of("name", targetIsland.getName())));
        } else {
            player.teleport(spawnLocation);
            MessageUtil.send(player, "tp.success", Map.of("name", targetIsland.getName()));
        }
        return true;
    }

    private void showIslandList(Player player, List<Island> islands) {
        MessageUtil.send(player, "tp.list.header");
        for (Island island : islands) {
            String ownerName = getPlayerName(island.getOwnerId());
            MessageUtil.send(player, "tp.list.entry", Map.of(
                    "id", island.getId(),
                    "owner", ownerName));
        }
        MessageUtil.send(player, "tp.list.footer");
    }

    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return plugin.getIslandManager().getAllIslands().stream()
                    .map(Island::getName)
                    .filter(Objects::nonNull)
                    .filter(name -> !name.isEmpty())
                    .distinct()
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }
        if (args.length == 3) {
            var matches = plugin.getIslandManager().getIslandsByName(args[1]);
            if (matches.size() > 1) {
                String prefix = args[2];
                return matches.stream()
                        .map(i -> String.valueOf(i.getId()))
                        .filter(id -> id.startsWith(prefix))
                        .toList();
            }
        }
        return List.of();
    }
}
