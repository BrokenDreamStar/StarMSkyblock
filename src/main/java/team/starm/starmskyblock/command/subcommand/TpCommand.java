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
import team.starm.starmskyblock.StarMSkyblock;

/**
 * 岛屿传送命令（/is tp <岛屿名称> [岛屿ID] [confirm]）
 * <p>
 * 按岛屿名称搜索目标岛屿并传送过去。重名时需用岛屿 ID 消歧；目标岛屿关闭 TP 设置则禁止传送。
 * 落点优先使用岛屿自定义 home，否则按配置偏移回到岛中心。落点不安全时需玩家追加 confirm 强制传送；
 * 若服务器配置了传送倒计时则走倒计时流程。
 */
public class TpCommand extends SubCommand {

    public TpCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 执行 /is tp 命令：解析目标岛屿、校验 TP 开关与落点安全后执行传送。
     */
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

    /** 列出所有重名岛屿的 ID 与岛主，供玩家用 ID 消歧。 */
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

    /**
     * Tab 补全：第二参数补全岛屿名称；第三参数在重名时补全候选岛屿 ID。
     */
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
