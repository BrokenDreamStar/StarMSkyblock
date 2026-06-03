package team.starm.starmskyblock.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.placeholder.handler.IslandListHandler;
import team.starm.starmskyblock.placeholder.handler.PermissionHandler;
import team.starm.starmskyblock.placeholder.handler.SettingsHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import team.starm.starmskyblock.config.GeneratorConfigManager;
import team.starm.starmskyblock.util.OreDisplayName;

public class SkyblockExpansion extends PlaceholderExpansion {

    private final StarMSkyblock plugin;

    private final IslandListHandler islandListHandler;
    private final PermissionHandler permissionHandler;
    private final SettingsHandler settingsHandler;

    public SkyblockExpansion(StarMSkyblock plugin) {
        this.plugin = plugin;
        this.islandListHandler = new IslandListHandler(plugin);
        this.permissionHandler = new PermissionHandler(plugin);
        this.settingsHandler = new SettingsHandler(plugin);
    }

    @Override
    public String getIdentifier() {
        return "starmskyblock";
    }

    @Override
    public String getAuthor() {
        return "StarM Team";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    public void setPlayerPage(Player player, int page) {
        islandListHandler.setPlayerPage(player, page);
    }

    public int getPlayerPage(Player player) {
        return islandListHandler.getPlayerPage(player);
    }

    public void resetPlayerPage(Player player) {
        islandListHandler.resetPlayerPage(player);
    }

    public IslandListHandler getIslandListHandler() {
        return islandListHandler;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {

        try {

            if (params == null || params.isBlank()) {
                return null;
            }

            if (player == null) {
                return null;
            }

            IslandManager islandManager = plugin.getIslandManager();

            int chunkX = player.getLocation().getChunk().getX();
            int chunkZ = player.getLocation().getChunk().getZ();

            if (params.equalsIgnoreCase("island_name_here")) {
                if (!plugin.getWorldManager().isSkyblockWorld(player.getWorld())) {
                    return "公共区域";
                }
                return getIslandName(islandManager, chunkX, chunkZ);
            }

            if (params.equalsIgnoreCase("island_name")) {
                return getPlayerOwnIslandName(islandManager, player.getUniqueId());
            }

            if (params.equalsIgnoreCase("role_here")) {
                if (!plugin.getWorldManager().isSkyblockWorld(player.getWorld())) {
                    return IslandPermissionLevel.VISITOR.getDisplayName();
                }
                return getPlayerRole(islandManager, chunkX, chunkZ, player.getUniqueId());
            }

            if (params.equalsIgnoreCase("role")) {
                return getPlayerOwnRole(islandManager, player.getUniqueId());
            }

            if (params.equalsIgnoreCase("level_here")) {
                if (!plugin.getWorldManager().isSkyblockWorld(player.getWorld())) {
                    return "&f-";
                }
                return getIslandLevelHere(islandManager, chunkX, chunkZ);
            }

            if (params.equalsIgnoreCase("generator_level_here")) {
                if (!plugin.getWorldManager().isSkyblockWorld(player.getWorld())) {
                    return "&f-";
                }
                return getGeneratorLevelHere(islandManager, chunkX, chunkZ);
            }

            if (params.equalsIgnoreCase("dimension")) {
                return switch (player.getWorld().getEnvironment()) {
                    case NORMAL -> "主世界";
                    case NETHER -> "下界";
                    case THE_END -> "末地";
                    default -> player.getWorld().getEnvironment().name();
                };
            }

            if (params.equalsIgnoreCase("own_island")) {
                Optional<Island> islandOpt =
                        islandManager.getIslandByPlayer(player.getUniqueId());
                if (islandOpt.isEmpty()) {
                    return "false";
                }
                Island island = islandOpt.get();
                return String.valueOf(island.isChunkWithinIsland(chunkX, chunkZ));
            }

            if (params.equalsIgnoreCase("creationtime")) {

                Optional<Island> islandOpt =
                        islandManager.getIslandByPlayer(player.getUniqueId());

                if (islandOpt.isEmpty()) {
                    return null;
                }

                String time = islandOpt.get().getCreatedAt();

                return time != null ? time : null;
            }

            if (params.equalsIgnoreCase("level")) {

                Optional<Island> islandOpt =
                        islandManager.getIslandByPlayer(player.getUniqueId());

                if (islandOpt.isEmpty()) {
                    return "&f-";
                }

                return String.valueOf(islandOpt.get().getLevel());
            }

            if (params.equalsIgnoreCase("generator_level")) {

                Optional<Island> islandOpt =
                        islandManager.getIslandByPlayer(player.getUniqueId());

                if (islandOpt.isEmpty()) {
                    return "&f-";
                }

                return String.valueOf(islandOpt.get().getGeneratorLevel());
            }

            if (params.startsWith("generator_")) {
                String dim = params.substring("generator_".length()).toLowerCase();
                if (Set.of("normal", "end", "nether").contains(dim)) {
                    return buildGeneratorDimensionString(player, dim);
                }
            }

            if (params.regionMatches(
                    true,
                    0,
                    IslandListHandler.PREFIX,
                    0,
                    IslandListHandler.PREFIX.length()
            )) {
                return islandListHandler.handle(player, params);
            }

            if (params.regionMatches(
                    true,
                    0,
                    SettingsHandler.PREFIX,
                    0,
                    SettingsHandler.PREFIX.length()
            )) {
                return settingsHandler.handle(player, params);
            }

            if (params.regionMatches(
                    true,
                    0,
                    PermissionHandler.PREFIX,
                    0,
                    PermissionHandler.PREFIX.length()
            )) {
                return permissionHandler.handle(player, params);
            }

            if (params.regionMatches(
                    true,
                    0,
                    PermissionHandler.HAS_PERMISSION_PREFIX,
                    0,
                    PermissionHandler.HAS_PERMISSION_PREFIX.length()
            )) {
                return permissionHandler.handle(player, params);
            }

        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return null;
    }

    private String getIslandName(
            IslandManager islandManager,
            int chunkX,
            int chunkZ
    ) {

        try {

            Optional<Island> islandOpt =
                    islandManager.getIslandAt(chunkX, chunkZ);

            if (islandOpt.isEmpty()) {
                islandOpt =
                        islandManager.getIslandAtMaxRange(chunkX, chunkZ);
            }

            if (islandOpt.isEmpty()) {
                return "公共区域";
            }

            Island island = islandOpt.get();

            String name = island.getName();

            if (name == null || name.isBlank()) {
                return "岛屿 #" + island.getId();
            }

            return name;

        } catch (Throwable ignored) {
        }

        return "公共区域";
    }

    private String getPlayerRole(
            IslandManager islandManager,
            int chunkX,
            int chunkZ,
            UUID playerUuid
    ) {

        try {

            Optional<Island> islandOpt =
                    islandManager.getIslandAt(chunkX, chunkZ);

            if (islandOpt.isEmpty()) {
                islandOpt =
                        islandManager.getIslandAtMaxRange(chunkX, chunkZ);
            }

            if (islandOpt.isEmpty()) {
                return IslandPermissionLevel.VISITOR.getDisplayName();
            }

            Island island = islandOpt.get();

            IslandPermissionLevel role =
                    island.getMemberRole(playerUuid);

            if (role != IslandPermissionLevel.VISITOR) {
                return role.getColor() + role.getDisplayName();
            }

            if (island.isCoop(playerUuid)) {
                return IslandPermissionLevel.COOP.getColor() + IslandPermissionLevel.COOP.getDisplayName();
            }

            return IslandPermissionLevel.VISITOR.getColor() + IslandPermissionLevel.VISITOR.getDisplayName();

        } catch (Throwable ignored) {
        }

        return IslandPermissionLevel.VISITOR.getColor() + IslandPermissionLevel.VISITOR.getDisplayName();
    }

    private String getIslandLevelHere(
            IslandManager islandManager,
            int chunkX,
            int chunkZ
    ) {
        try {
            Optional<Island> islandOpt =
                    islandManager.getIslandAt(chunkX, chunkZ);

            if (islandOpt.isEmpty()) {
                islandOpt =
                        islandManager.getIslandAtMaxRange(chunkX, chunkZ);
            }

            if (islandOpt.isEmpty()) {
                return "&f-";
            }

            return String.valueOf(islandOpt.get().getLevel());

        } catch (Throwable ignored) {
        }

        return "&f-";
    }

    private String getGeneratorLevelHere(
            IslandManager islandManager,
            int chunkX,
            int chunkZ
    ) {
        try {
            Optional<Island> islandOpt =
                    islandManager.getIslandAt(chunkX, chunkZ);

            if (islandOpt.isEmpty()) {
                islandOpt =
                        islandManager.getIslandAtMaxRange(chunkX, chunkZ);
            }

            if (islandOpt.isEmpty()) {
                return "&f-";
            }

            return String.valueOf(islandOpt.get().getGeneratorLevel());

        } catch (Throwable ignored) {
        }

        return "&f-";
    }

    private String getPlayerOwnIslandName(
            IslandManager islandManager,
            UUID playerUuid
    ) {
        try {
            Optional<Island> islandOpt =
                    islandManager.getIslandByPlayer(playerUuid);

            if (islandOpt.isEmpty()) {
                return null;
            }

            Island island = islandOpt.get();

            String name = island.getName();

            if (name == null || name.isBlank()) {
                return "岛屿 #" + island.getId();
            }

            return name;

        } catch (Throwable ignored) {
        }

        return null;
    }

    private String buildGeneratorDimensionString(Player player, String dim) {
        Optional<Island> islandOpt = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (islandOpt.isEmpty()) return "&f-";

        Island island = islandOpt.get();
        GeneratorConfigManager.GeneratorTier tier = plugin.getGeneratorConfigManager()
                .getTier(island.getGeneratorLevel());

        Map<String, Integer> rates = switch (dim) {
            case "normal" -> tier.normal();
            case "end" -> tier.end();
            case "nether" -> tier.nether();
            default -> Map.of();
        };
        if (rates.isEmpty()) return "&f-";

        Map<String, Boolean> enabledMap = new LinkedHashMap<>();
        Set<String> disabled = island.getDisabledGeneratorOres().get(dim);
        int totalWeight = 0;
        for (int w : rates.values()) totalWeight += w;

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : rates.entrySet()) {
            String material = entry.getKey();
            int weight = entry.getValue();
            double pct = totalWeight > 0 ? (weight * 100.0 / totalWeight) : 0;
            boolean enabled = disabled == null || !disabled.contains(material);

            if (!sb.isEmpty()) sb.append("\n");
            sb.append("&7- &f").append(OreDisplayName.toChinese(material));
            sb.append("&7: ");
            sb.append(String.format("%.1f%% ", pct));
            sb.append(enabled ? "&a✓" : "&c✗");
        }

        return sb.toString();
    }

    private String getPlayerOwnRole(
            IslandManager islandManager,
            UUID playerUuid
    ) {
        try {
            Optional<Island> islandOpt =
                    islandManager.getIslandByPlayer(playerUuid);

            if (islandOpt.isEmpty()) {
                return IslandPermissionLevel.VISITOR.getDisplayName();
            }

            Island island = islandOpt.get();

            IslandPermissionLevel role =
                    island.getMemberRole(playerUuid);

            if (role != IslandPermissionLevel.VISITOR) {
                return role.getColor() + role.getDisplayName();
            }

            if (island.isCoop(playerUuid)) {
                return IslandPermissionLevel.COOP.getColor() + IslandPermissionLevel.COOP.getDisplayName();
            }

            return IslandPermissionLevel.VISITOR.getColor() + IslandPermissionLevel.VISITOR.getDisplayName();

        } catch (Throwable ignored) {
        }

        return IslandPermissionLevel.VISITOR.getColor() + IslandPermissionLevel.VISITOR.getDisplayName();
    }
}
