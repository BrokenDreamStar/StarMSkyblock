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
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.task.placeholder.TaskPlaceholderHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import net.milkbowl.vault.economy.Economy;
import team.starm.starmskyblock.config.GeneratorConfigManager;
import team.starm.starmskyblock.config.UpgradeConfigManager;
import team.starm.starmskyblock.util.OreDisplayName;

import java.util.HashMap;
import java.util.Locale;
import org.bukkit.Location;

/**
 * PlaceholderAPI 扩展 -- 向外暴露 {@code starmskyblock} 占位符标识符。
 * <p>
 * 注册到 PlaceholderAPI 后，外部插件/显示板可通过 {@code %starmskyblock_<params>%}
 * 读取岛屿名称、等级、身份、刷石机配置、升级金额等信息。
 * <p>
 * 派发策略：
 * <ul>
 *   <li>精确匹配 placeholder 走 {@link #exactDispatch} Map，O(1) 哈希查找</li>
 *   <li>带前缀的 placeholder（如 {@code generator_*}/{@code island_list_*}）走 fallback chain，
 *       委托至 {@link IslandListHandler}/{@link SettingsHandler}/{@link PermissionHandler}/
 *       {@code TaskPlaceholderHandler}</li>
 * </ul>
 */
public class SkyblockExpansion extends PlaceholderExpansion {

    private final StarMSkyblock plugin;

    private final IslandListHandler islandListHandler;
    private final PermissionHandler permissionHandler;
    private final SettingsHandler settingsHandler;
    private final TaskPlaceholderHandler taskHandler;

    /**
     * 精确匹配 placeholder 派发表(键已小写化)。
     * 取代原 {@code onPlaceholderRequest} 内 30+ 个 {@code equalsIgnoreCase} 串,
     * 将 O(n) 线性扫描压为 O(1) 哈希查找。带前缀的 placeholder(如 {@code generator_*}
     * /{@code upgrades_*})不在此表,仍走下方 fallback chain。
     */
    private final Map<String, Function<LazyContext, String>> exactDispatch = new HashMap<>();

    public SkyblockExpansion(StarMSkyblock plugin) {
        this.plugin = plugin;
        this.islandListHandler = new IslandListHandler(plugin);
        this.permissionHandler = new PermissionHandler(plugin);
        this.settingsHandler = new SettingsHandler(plugin);
        this.taskHandler = new TaskPlaceholderHandler(plugin);
        registerExactDispatch();
    }

    private void registerExactDispatch() {
        // 注:lambda 捕获 this.plugin,执行时再解析当前 plugin 状态(而非构造时快照)
        exactDispatch.put("island_name_here", ctx -> {
            if (!plugin.getWorldManager().isSkyblockWorld(ctx.player.getWorld())) {
                return MessageUtil.format("placeholder.public-area");
            }
            return getIslandName(ctx.chunkX(), ctx.chunkZ());
        });
        exactDispatch.put("island_name", ctx ->
                getPlayerOwnIslandName(ctx.player.getUniqueId()));
        exactDispatch.put("role_here", ctx -> {
            if (!plugin.getWorldManager().isSkyblockWorld(ctx.player.getWorld())) {
                return IslandPermissionLevel.VISITOR.getDisplayName();
            }
            return getPlayerRole(ctx.chunkX(), ctx.chunkZ(), ctx.player.getUniqueId());
        });
        exactDispatch.put("role", ctx ->
                getPlayerOwnRole(ctx.player.getUniqueId()));
        exactDispatch.put("level_here", ctx -> {
            if (!plugin.getWorldManager().isSkyblockWorld(ctx.player.getWorld())) {
                return "&f-";
            }
            return getIslandLevelHere(ctx.chunkX(), ctx.chunkZ());
        });
        exactDispatch.put("total_points_here", ctx -> {
            if (!plugin.getWorldManager().isSkyblockWorld(ctx.player.getWorld())) {
                return "&f-";
            }
            return getIslandValueHere(ctx.chunkX(), ctx.chunkZ(), "total_points");
        });
        exactDispatch.put("experience_here", ctx -> {
            if (!plugin.getWorldManager().isSkyblockWorld(ctx.player.getWorld())) {
                return "&f-";
            }
            return getIslandValueHere(ctx.chunkX(), ctx.chunkZ(), "experience");
        });
        exactDispatch.put("blocks_counted_here", ctx -> {
            if (!plugin.getWorldManager().isSkyblockWorld(ctx.player.getWorld())) {
                return "&f-";
            }
            return getIslandValueHere(ctx.chunkX(), ctx.chunkZ(), "blocks_counted");
        });
        exactDispatch.put("generator_level_here", ctx -> {
            if (!plugin.getWorldManager().isSkyblockWorld(ctx.player.getWorld())) {
                return "&f-";
            }
            return getGeneratorLevelHere(ctx.chunkX(), ctx.chunkZ());
        });
        exactDispatch.put("dimension", ctx -> switch (ctx.player.getWorld().getEnvironment()) {
            case NORMAL -> MessageUtil.format("dimension.normal");
            case NETHER -> MessageUtil.format("dimension.nether");
            case THE_END -> MessageUtil.format("dimension.end");
            default -> ctx.player.getWorld().getEnvironment().name();
        });
        exactDispatch.put("own_island", ctx -> {
            Optional<Island> islandOpt = ctx.playerIsland();
            if (islandOpt.isEmpty()) return "false";
            Island island = islandOpt.get();
            return String.valueOf(island.isChunkWithinIsland(ctx.chunkX(), ctx.chunkZ()));
        });
        exactDispatch.put("has_island", ctx -> String.valueOf(ctx.playerIsland().isPresent()));
        exactDispatch.put("creationtime", ctx -> {
            Optional<Island> islandOpt = ctx.playerIsland();
            if (islandOpt.isEmpty()) return null;
            String time = islandOpt.get().getCreatedAt();
            return time != null ? time : null;
        });
        exactDispatch.put("level", ctx -> {
            Optional<Island> islandOpt = ctx.playerIsland();
            if (islandOpt.isEmpty()) return "&f-";
            return String.valueOf(islandOpt.get().getLevel());
        });
        exactDispatch.put("total_points", ctx -> {
            Optional<Island> islandOpt = ctx.playerIsland();
            if (islandOpt.isEmpty()) return "&f-";
            return String.format("%.2f", islandOpt.get().getExperience());
        });
        exactDispatch.put("experience", ctx -> {
            Optional<Island> islandOpt = ctx.playerIsland();
            if (islandOpt.isEmpty()) return "&f-";
            return String.format("%.2f", islandOpt.get().getExperience());
        });
        exactDispatch.put("blocks_counted", ctx -> {
            Optional<Island> islandOpt = ctx.playerIsland();
            if (islandOpt.isEmpty()) return "&f-";
            long total = 0;
            for (long count : islandOpt.get().getBlockCounts().values()) {
                total += count;
            }
            return String.valueOf(total);
        });
        exactDispatch.put("generator_level", ctx -> {
            Optional<Island> islandOpt = ctx.playerIsland();
            if (islandOpt.isEmpty()) return "&f-";
            return String.valueOf(islandOpt.get().getGeneratorLevel());
        });
        exactDispatch.put("generator_level_next", ctx -> {
            Optional<Island> islandOpt = ctx.playerIsland();
            if (islandOpt.isEmpty()) return "&f-";
            GeneratorConfigManager genConfig = plugin.getGeneratorConfigManager();
            int currentLevel = islandOpt.get().getGeneratorLevel();
            Optional<GeneratorConfigManager.GeneratorTier> nextTierOpt = genConfig.getNextTier(currentLevel);
            if (nextTierOpt.isEmpty()) return MessageUtil.format("placeholder.max-level-reached");
            return buildGeneratorLevelString(nextTierOpt.get());
        });
        exactDispatch.put("upgrades_generator_next_level_money", ctx -> {
            var islandOpt = ctx.playerIsland();
            if (islandOpt.isEmpty()) return "&f-";
            Island island = islandOpt.get();
            UpgradeConfigManager upgradeConfig = plugin.getUpgradeConfigManager();
            var next = upgradeConfig.getNextGeneratorUpgrade(island.getGeneratorLevel());
            if (next.isEmpty()) return MessageUtil.format("placeholder.max-level-reached");
            return String.valueOf((long) next.get().money());
        });
        exactDispatch.put("upgrades_island_radius_next_level_money", ctx -> {
            var islandOpt = ctx.playerIsland();
            if (islandOpt.isEmpty()) return "&f-";
            Island island = islandOpt.get();
            UpgradeConfigManager upgradeConfig = plugin.getUpgradeConfigManager();
            var next = upgradeConfig.getNextRadiusUpgrade(island.getRadius());
            if (next.isEmpty()) return MessageUtil.format("placeholder.max-level-reached");
            return String.valueOf((long) next.get().money());
        });
        exactDispatch.put("upgrades_generator_has_money", ctx -> {
            var islandOpt = ctx.playerIsland();
            if (islandOpt.isEmpty()) return "false";
            Island island = islandOpt.get();
            UpgradeConfigManager upgradeConfig = plugin.getUpgradeConfigManager();
            var next = upgradeConfig.getNextGeneratorUpgrade(island.getGeneratorLevel());
            if (next.isEmpty()) return "false";
            Economy economy = plugin.getEconomy();
            if (economy == null) return "false";
            return String.valueOf(economy.has(ctx.player, next.get().money()));
        });
        exactDispatch.put("upgrades_island_radius_has_money", ctx -> {
            var islandOpt = ctx.playerIsland();
            if (islandOpt.isEmpty()) return "false";
            Island island = islandOpt.get();
            UpgradeConfigManager upgradeConfig = plugin.getUpgradeConfigManager();
            var next = upgradeConfig.getNextRadiusUpgrade(island.getRadius());
            if (next.isEmpty()) return "false";
            Economy economy = plugin.getEconomy();
            if (economy == null) return "false";
            return String.valueOf(economy.has(ctx.player, next.get().money()));
        });
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
        return plugin.getPluginMeta().getVersion();
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

    /**
     * PlaceholderAPI 主入口 -- 依 params 派发到精确匹配表或前缀 fallback chain。
     * <p>任一分支抛出的异常都会被顶层 catch 兜住并记录日志，返回 null（PAPI 对 null 静默处理）。
     *
     * @param player 请求占位符的玩家
     * @param params 占位符参数（不含前缀标识符）
     * @return 渲染后的字符串，无法识别时返回 null
     */
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
            LazyContext ctx = new LazyContext(player, islandManager);

            // 精确匹配 placeholder -- Map 派发,O(1)
            Function<LazyContext, String> exactHandler = exactDispatch.get(params.toLowerCase(Locale.ROOT));
            if (exactHandler != null) {
                return exactHandler.apply(ctx);
            }

            // 带前缀的 placeholder -- fallback chain
            if (params.startsWith("generator_level_next_")) {
                String dim = params.substring("generator_level_next_".length()).toLowerCase();
                if (!Set.of("normal", "nether", "end").contains(dim)) return "&f-";

                Optional<Island> islandOpt = ctx.playerIsland();
                if (islandOpt.isEmpty()) return "&f-";

                GeneratorConfigManager genConfig = plugin.getGeneratorConfigManager();
                int currentLevel = islandOpt.get().getGeneratorLevel();
                Optional<GeneratorConfigManager.GeneratorTier> nextTierOpt = genConfig.getNextTier(currentLevel);
                if (nextTierOpt.isEmpty()) return MessageUtil.format("placeholder.max-level-reached");

                GeneratorConfigManager.GeneratorTier nextTier = nextTierOpt.get();
                Map<String, Double> rates = switch (dim) {
                    case "normal" -> nextTier.normal();
                    case "nether" -> nextTier.nether();
                    case "end" -> nextTier.end();
                    default -> Map.of();
                };
                if (rates.isEmpty()) return "&f-";
                return buildDimensionRatesString(rates);
            }

            if (params.startsWith("generator_level_")) {
                String rest = params.substring("generator_level_".length());
                String[] parts = rest.split("_", 2);
                try {
                    int level = Integer.parseInt(parts[0]);
                    GeneratorConfigManager genConfig = plugin.getGeneratorConfigManager();
                    if (level < 1 || level > genConfig.getMaxLevel()) return "&f-";

                    GeneratorConfigManager.GeneratorTier tier = genConfig.getTier(level);

                    if (parts.length == 1) {
                        return buildGeneratorLevelString(tier);
                    }

                    String dim = parts[1].toLowerCase();
                    Map<String, Double> rates = switch (dim) {
                        case "normal" -> tier.normal();
                        case "nether" -> tier.nether();
                        case "end" -> tier.end();
                        default -> null;
                    };
                    if (rates == null || rates.isEmpty()) return "&f-";
                    return buildDimensionRatesString(rates);
                } catch (NumberFormatException e) {
                    return "&f-";
                }
            }

            if (params.startsWith("generator_")) {
                String rest = params.substring("generator_".length()).toLowerCase();

                boolean booleanMode = rest.endsWith("_boolean");
                if (booleanMode) {
                    rest = rest.substring(0, rest.length() - "_boolean".length());
                }

                if (Set.of("normal", "end", "nether").contains(rest)) {
                    return buildGeneratorDimensionString(ctx.playerIsland(), player, rest);
                }

                int underscoreIndex = rest.indexOf('_');
                if (underscoreIndex > 0) {
                    String dim = rest.substring(0, underscoreIndex);
                    if (Set.of("normal", "nether", "end").contains(dim)) {
                        String oreName = rest.substring(underscoreIndex + 1);

                        if (oreName.equals("all")) {
                            return String.valueOf(isAllGeneratorOresEnabled(ctx.playerIsland(), player, dim));
                        }

                        if (booleanMode) {
                            return String.valueOf(isGeneratorOreEnabled(ctx.playerIsland(), player, dim, oreName));
                        }
                        return getGeneratorOreDimensionStatus(ctx.playerIsland(), player, dim, oreName);
                    }
                }

                if (booleanMode) {
                    String dim = switch (player.getWorld().getEnvironment()) {
                        case NETHER -> "nether";
                        case THE_END -> "end";
                        default -> "normal";
                    };
                    return String.valueOf(isGeneratorOreEnabled(ctx.playerIsland(), player, dim, rest));
                }
                return getGeneratorOreStatus(ctx.playerIsland(), player, rest);
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

            if (params.regionMatches(
                    true,
                    0,
                    TaskPlaceholderHandler.PREFIX,
                    0,
                    TaskPlaceholderHandler.PREFIX.length()
            )) {
                return taskHandler.handle(player, params);
            }

        } catch (Throwable throwable) {
            MessageUtil.consoleError("处理 PlaceholderAPI 请求时发生错误", throwable);
        }

        return null;
    }

    // ==================== 岛屿查找 + 渲染 helper（#24 去重）====================

    /**
     * 在 (chunkX, chunkZ) 查找岛屿：先精确匹配区块，再尝试最大范围回退。
     * 抽取自原 getIslandName/getPlayerRole/getIslandLevelHere/getIslandValueHere/getGeneratorLevelHere
     * 中重复出现的 "getIslandAt -> ifEmpty getIslandAtMaxRange" 双重查找。
     */
    private Optional<Island> findIslandAt(int chunkX, int chunkZ) {
        IslandManager islandManager = plugin.getIslandManager();
        Optional<Island> opt = islandManager.getIslandAt(chunkX, chunkZ);
        if (opt.isEmpty()) {
            opt = islandManager.getIslandAtMaxRange(chunkX, chunkZ);
        }
        return opt;
    }

    /**
     * 带异常兜底与默认值的"按位置查找岛屿"渲染。
     * 查找失败或提取器抛异常时返回 fallback，并记录一次警告日志。
     */
    private <T> T withIslandAt(int chunkX, int chunkZ, T fallback, Function<Island, T> extractor) {
        try {
            return findIslandAt(chunkX, chunkZ).map(extractor).orElse(fallback);
        } catch (RuntimeException e) {
            MessageUtil.consoleWarn("SkyblockExpansion placeholder 渲染失败: " + e.getMessage());
            return fallback;
        }
    }

    /**
     * 带异常兜底与默认值的"按玩家查找所属岛屿"渲染。
     */
    private <T> T withOwnIsland(UUID playerUuid, T fallback, Function<Island, T> extractor) {
        try {
            return plugin.getIslandManager().getIslandByPlayer(playerUuid).map(extractor).orElse(fallback);
        } catch (RuntimeException e) {
            MessageUtil.consoleWarn("SkyblockExpansion placeholder 渲染失败: " + e.getMessage());
            return fallback;
        }
    }

    /** 访客身份显示（带颜色前缀），用于 role 类 placeholder 的回退值 */
    private String visitorRoleDisplay() {
        IslandPermissionLevel v = IslandPermissionLevel.VISITOR;
        return v.getColor() + v.getDisplayName();
    }

    /** 格式化玩家在岛屿中的身份显示：成员 -> 合作者 -> 访客 */
    private String formatRole(Island island, UUID playerUuid) {
        IslandPermissionLevel role = island.getMemberRole(playerUuid);
        if (role != IslandPermissionLevel.VISITOR) {
            return role.getColor() + role.getDisplayName();
        }
        if (island.isCoop(playerUuid)) {
            return IslandPermissionLevel.COOP.getColor() + IslandPermissionLevel.COOP.getDisplayName();
        }
        return visitorRoleDisplay();
    }

    private String getIslandName(int chunkX, int chunkZ) {
        return withIslandAt(chunkX, chunkZ, MessageUtil.format("placeholder.public-area"), island -> {
            String name = island.getName();
            return (name == null || name.isBlank())
                    ? MessageUtil.format("placeholder.island-id", Map.of("id", island.getId()))
                    : name;
        });
    }

    private String getPlayerRole(int chunkX, int chunkZ, UUID playerUuid) {
        return withIslandAt(chunkX, chunkZ, IslandPermissionLevel.VISITOR.getDisplayName(),
                island -> formatRole(island, playerUuid));
    }

    private String getIslandLevelHere(int chunkX, int chunkZ) {
        return withIslandAt(chunkX, chunkZ, "&f-", island -> String.valueOf(island.getLevel()));
    }

    private String getIslandValueHere(int chunkX, int chunkZ, String type) {
        return withIslandAt(chunkX, chunkZ, "&f-", island -> {
            if ("total_points".equals(type) || "experience".equals(type)) {
                return String.format("%.2f", island.getExperience());
            } else if ("blocks_counted".equals(type)) {
                long total = 0;
                for (long count : island.getBlockCounts().values()) {
                    total += count;
                }
                return String.valueOf(total);
            }
            return "&f-";
        });
    }

    private String getGeneratorLevelHere(int chunkX, int chunkZ) {
        return withIslandAt(chunkX, chunkZ, "&f-", island -> String.valueOf(island.getGeneratorLevel()));
    }

    private String getPlayerOwnIslandName(UUID playerUuid) {
        return withOwnIsland(playerUuid, null, island -> {
            String name = island.getName();
            return (name == null || name.isBlank())
                    ? MessageUtil.format("placeholder.island-id", Map.of("id", island.getId()))
                    : name;
        });
    }

    private String getPlayerOwnRole(UUID playerUuid) {
        return withOwnIsland(playerUuid, IslandPermissionLevel.VISITOR.getDisplayName(),
                island -> formatRole(island, playerUuid));
    }

    private String buildGeneratorDimensionString(Optional<Island> islandOpt, Player player, String dim) {
        if (islandOpt.isEmpty()) return "&f-";

        Island island = islandOpt.get();
        GeneratorConfigManager.GeneratorTier tier = plugin.getGeneratorConfigManager()
                .getTier(island.getGeneratorLevel());

        Map<String, Double> rates = switch (dim) {
            case "normal" -> tier.normal();
            case "end" -> tier.end();
            case "nether" -> tier.nether();
            default -> Map.of();
        };
        if (rates.isEmpty()) return "&f-";

        Map<String, Boolean> enabledMap = new LinkedHashMap<>();
        Set<String> disabled = island.getDisabledGeneratorOres().get(dim);
        double totalWeight = 0;
        for (double w : rates.values()) totalWeight += w;

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            String material = entry.getKey();
            double weight = entry.getValue();
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

    private String buildGeneratorLevelString(GeneratorConfigManager.GeneratorTier tier) {

        StringBuilder sb = new StringBuilder();
        appendDimensionRates(sb, MessageUtil.format("dimension.normal"), tier.normal());
        sb.append("\n");
        appendDimensionRates(sb, MessageUtil.format("dimension.nether"), tier.nether());
        sb.append("\n");
        appendDimensionRates(sb, MessageUtil.format("dimension.end"), tier.end());

        return sb.toString();
    }

    private void appendDimensionRates(StringBuilder sb, String displayName, Map<String, Double> rates) {
        if (rates.isEmpty()) return;
        sb.append("&a▶ ").append(displayName);
        double totalWeight = 0;
        for (double w : rates.values()) totalWeight += w;
        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            double pct = totalWeight > 0 ? (entry.getValue() * 100.0 / totalWeight) : 0;
            sb.append("\n  &e").append(OreDisplayName.toChinese(entry.getKey()));
            sb.append("&7: ").append(String.format("%.1f%%", pct));
        }
    }

    private String buildDimensionRatesString(Map<String, Double> rates) {
        double totalWeight = 0;
        for (double w : rates.values()) totalWeight += w;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            double pct = totalWeight > 0 ? (entry.getValue() * 100.0 / totalWeight) : 0;
            if (!sb.isEmpty()) sb.append("\n");
            sb.append("&7- &f").append(OreDisplayName.toChinese(entry.getKey()));
            sb.append("&7: ").append(String.format("%.1f%%", pct));
        }
        return sb.toString();
    }

    private boolean isAllGeneratorOresEnabled(Optional<Island> islandOpt, Player player, String dim) {
        if (islandOpt.isEmpty()) return false;

        Island island = islandOpt.get();
        GeneratorConfigManager genConfig = plugin.getGeneratorConfigManager();
        GeneratorConfigManager.GeneratorTier tier = genConfig.getTier(island.getGeneratorLevel());

        Map<String, Double> rates = switch (dim) {
            case "normal" -> tier.normal();
            case "nether" -> tier.nether();
            case "end" -> tier.end();
            default -> Map.of();
        };

        if (rates.isEmpty()) return false;

        Set<String> disabled = island.getDisabledGeneratorOres().get(dim);
        if (disabled == null || disabled.isEmpty()) return true;

        for (String material : rates.keySet()) {
            if (disabled.contains(material)) return false;
        }
        return true;
    }

    private boolean isGeneratorOreEnabled(Optional<Island> islandOpt, Player player, String dim, String oreName) {
        if (islandOpt.isEmpty()) return false;

        Island island = islandOpt.get();
        GeneratorConfigManager genConfig = plugin.getGeneratorConfigManager();
        GeneratorConfigManager.GeneratorTier tier = genConfig.getTier(island.getGeneratorLevel());

        Map<String, Double> rates = switch (dim) {
            case "normal" -> tier.normal();
            case "nether" -> tier.nether();
            case "end" -> tier.end();
            default -> Map.of();
        };

        String upper = oreName.toUpperCase();
        String material = rates.containsKey(upper) ? upper : null;
        if (material == null || !rates.containsKey(material)) return false;

        Set<String> disabled = island.getDisabledGeneratorOres().get(dim);
        return disabled == null || !disabled.contains(material);
    }

    private String getGeneratorOreDimensionStatus(Optional<Island> islandOpt, Player player, String dim, String oreName) {
        if (islandOpt.isEmpty()) return "&f-";

        Island island = islandOpt.get();
        GeneratorConfigManager genConfig = plugin.getGeneratorConfigManager();
        GeneratorConfigManager.GeneratorTier tier = genConfig.getTier(island.getGeneratorLevel());

        Map<String, Double> rates = switch (dim) {
            case "normal" -> tier.normal();
            case "nether" -> tier.nether();
            case "end" -> tier.end();
            default -> Map.of();
        };

        String upper = oreName.toUpperCase();
        String material = rates.containsKey(upper) ? upper : null;
        if (material == null || !rates.containsKey(material)) return "&f-";

        Set<String> disabled = island.getDisabledGeneratorOres().get(dim);
        return (disabled == null || !disabled.contains(material))
                ? MessageUtil.format("placeholder.yes")
                : MessageUtil.format("placeholder.no");
    }

    private String getGeneratorOreStatus(Optional<Island> islandOpt, Player player, String oreName) {
        if (islandOpt.isEmpty()) return "false";

        Island island = islandOpt.get();
        GeneratorConfigManager genConfig = plugin.getGeneratorConfigManager();
        GeneratorConfigManager.GeneratorTier tier = genConfig.getTier(island.getGeneratorLevel());

        String dim = switch (player.getWorld().getEnvironment()) {
            case NETHER -> "nether";
            case THE_END -> "end";
            default -> "normal";
        };

        Map<String, Double> rates = switch (dim) {
            case "normal" -> tier.normal();
            case "nether" -> tier.nether();
            case "end" -> tier.end();
            default -> Map.of();
        };

        String upper = oreName.toUpperCase();
        String material = rates.containsKey(upper) ? upper : null;
        if (material == null || !rates.containsKey(material)) return "false";

        Set<String> disabled = island.getDisabledGeneratorOres().get(dim);
        return (disabled == null || !disabled.contains(material))
                ? MessageUtil.format("placeholder.yes")
                : MessageUtil.format("placeholder.no");
    }

    /**
     * 占位符请求的惰性求值上下文 -- 仅在实际需要时才解析 chunk 坐标与所属岛屿，
     * 避免每个占位符都付出 {@code player.getLocation()} 与 island grid 查询的开销。
     */
    private static final class LazyContext {
        final Player player;
        final IslandManager islandManager;
        private int chunkX;
        private int chunkZ;
        private boolean chunkResolved;
        // volatile:同一 LazyContext 实例理论上只在单一线程使用,但保持可见性语义以匹配
        // 后续可能的跨线程使用,且消除字段发布(JMM)上的潜在重排隐患。
        private volatile Optional<Island> playerIsland;

        LazyContext(Player player, IslandManager islandManager) {
            this.player = player;
            this.islandManager = islandManager;
        }

        int chunkX() {
            if (!chunkResolved) resolveChunk();
            return chunkX;
        }

        int chunkZ() {
            if (!chunkResolved) resolveChunk();
            return chunkZ;
        }

        private void resolveChunk() {
            // 用位运算避免 location.getChunk() 强制加载区块,且只取一次 Location
            Location loc = player.getLocation();
            chunkX = loc.getBlockX() >> 4;
            chunkZ = loc.getBlockZ() >> 4;
            chunkResolved = true;
        }

        Optional<Island> playerIsland() {
            Optional<Island> snapshot = playerIsland;
            if (snapshot == null) {
                snapshot = islandManager.getIslandByPlayer(player.getUniqueId());
                playerIsland = snapshot;
            }
            return snapshot;
        }
    }
}
