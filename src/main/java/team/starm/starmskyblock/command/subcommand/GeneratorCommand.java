package team.starm.starmskyblock.command.subcommand;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.GeneratorConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.message.NameTranslator;
import team.starm.starmskyblock.permission.IslandPermission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 岛屿发电机产物开关命令（/is generator [维度] [矿石|all] [true|false|toggle]）
 * <p>
 * 查看或切换岛屿发电机各维度的产物启用状态。发电机等级决定可用产物及权重，
 * 玩家可逐个或批量（all）启停非默认产物；默认产物（主世界圆石、末地末地石、下界玄武岩）不可禁用。
 * 修改需 SET_GENERATOR 权限，结果以禁用集合 JSON 持久化。消息中矿石名走客户端可翻译组件。
 */
public class GeneratorCommand extends SubCommand {

    /** 三个维度的 key，用于参数解析与状态遍历。 */
    private static final List<String> DIMENSIONS = List.of("normal", "nether", "end");
    /** 第四参数的合法取值。 */
    private static final List<String> TOGGLE_VALUES = List.of("true", "false", "toggle");
    /** 维度 key -> 中文显示名，用于消息展示。 */
    private static final Map<String, String> DIMENSION_DISPLAY = new LinkedHashMap<>();

    static {
        DIMENSION_DISPLAY.put("normal", "主世界");
        DIMENSION_DISPLAY.put("nether", "下界");
        DIMENSION_DISPLAY.put("end", "末地");
    }

    /**
     * 每个维度的默认产物（不可被禁用）。
     * normal/end → 圆石, nether → 玄武岩
     */
    private static final Map<String, String> DIMENSION_DEFAULT_ORE = Map.of(
            "normal", "COBBLESTONE",
            "end", "END_STONE",
            "nether", "BASALT"
    );

    public GeneratorCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 执行 /is generator 命令：按参数层级解析维度、矿石与开关值，分发到状态查看或单/批量切换。
     */
    @Override
    public boolean execute(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 4, "/is generator [维度] [矿石] [true/false/toggle]")) return true;

        Optional<Island> islandOpt = getIsland(player);
        if (islandOpt.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = islandOpt.get();

        // /is generator — 查看所有维度状态
        if (args.length == 1) {
            showGeneratorStatus(player, island, null);
            return true;
        }

        String dimension = args[1].toLowerCase();

        // /is generator <维度> — 查看指定维度状态
        if (args.length == 2) {
            if (!DIMENSIONS.contains(dimension)) {
                MessageUtil.send(player, "generator.invalid-dimension");
                return true;
            }
            showGeneratorStatus(player, island, dimension);
            return true;
        }

        // /is generator <维度> all <true/false/toggle> — 批量切换所有矿石
        if (args[2].equalsIgnoreCase("all")) {
            if (!island.hasPermission(player.getUniqueId(), IslandPermission.SET_GENERATOR)) {
                MessageUtil.send(player, "generator.no-permission");
                return true;
            }

            GeneratorConfigManager.GeneratorTier tier = plugin.getGeneratorConfigManager()
                    .getTier(island.getGeneratorLevel());
            Map<String, Double> rates = getDimensionRates(tier, dimension);
            if (rates.isEmpty()) {
                MessageUtil.send(player, "generator.dimension-empty");
                return true;
            }

            String defaultOre = DIMENSION_DEFAULT_ORE.get(dimension);

            int toggled = 0;
            if (args.length < 4) {
                MessageUtil.send(player, "generator.usage.all", Map.of("dimension", dimension));
                return true;
            }
            Boolean enableAll;
            if (args[3].equalsIgnoreCase("true")) {
                enableAll = Boolean.TRUE;
            } else if (args[3].equalsIgnoreCase("false")) {
                enableAll = Boolean.FALSE;
            } else {
                MessageUtil.send(player, "generator.invalid-boolean-value");
                return true;
            }

            for (String mat : rates.keySet()) {
                if (mat.equals(defaultOre)) continue;
                island.toggleGeneratorOre(dimension, mat, enableAll);
                toggled++;
            }

            String json = island.getDisabledGeneratorOresJson();
            plugin.getIslandManager().updateIslandGeneratorDisabled(island.getId(), json);

            String dimDisplay = DIMENSION_DISPLAY.getOrDefault(dimension, dimension);
            if (enableAll) {
                MessageUtil.send(player, "generator.all-enabled",
                        Map.of("dimension", dimDisplay, "count", toggled));
            } else {
                MessageUtil.send(player, "generator.all-disabled",
                        Map.of("dimension", dimDisplay, "count", toggled));
            }
            return true;
        }

        // /is generator <维度> <矿石> <true/false/toggle> — 切换单个矿石
        if (!island.hasPermission(player.getUniqueId(), IslandPermission.SET_GENERATOR)) {
            MessageUtil.send(player, "generator.no-permission");
            return true;
        }

        GeneratorConfigManager.GeneratorTier tier = plugin.getGeneratorConfigManager()
                .getTier(island.getGeneratorLevel());
        Map<String, Double> rates = getDimensionRates(tier, dimension);

        String materialName = resolveOreName(args[2], rates);
        if (materialName == null) {
            MessageUtil.send(player, "generator.ore-not-found", Map.of("dimension", dimension));
            return true;
        }

        // 禁止禁用维度默认产物
        String defaultOre = DIMENSION_DEFAULT_ORE.get(dimension);
        if (materialName.equals(defaultOre)) {
            String dimDisplay = DIMENSION_DISPLAY.getOrDefault(dimension, dimension);
            if (!MessageUtil.isSilent(player.getUniqueId())) {
                ((Audience) player).sendMessage(Component.textOfChildren(
                        NameTranslator.translatable(defaultOre).color(NamedTextColor.YELLOW),
                        MessageUtil.parse(MessageUtil.format("generator.default-ore-cannot-disable",
                                Map.of("dimension", dimDisplay)))
                ));
            }
            return true;
        }

        Boolean enable = null;
        if (args.length >= 4) {
            String toggleArg = args[3].toLowerCase();
            if (toggleArg.equals("toggle")) {
                enable = null;
            } else if (toggleArg.equals("true")) {
                enable = true;
            } else if (toggleArg.equals("false")) {
                enable = false;
            } else {
                MessageUtil.send(player, "generator.invalid-toggle-value");
                return true;
            }
        }

        boolean result = island.toggleGeneratorOre(dimension, materialName, enable);
        String json = island.getDisabledGeneratorOresJson();
        plugin.getIslandManager().updateIslandGeneratorDisabled(island.getId(), json);

        String dimDisplay = DIMENSION_DISPLAY.getOrDefault(dimension, dimension);
        if (!MessageUtil.isSilent(player.getUniqueId())) {
            ((Audience) player).sendMessage(Component.textOfChildren(
                    MessageUtil.parse(MessageUtil.format("generator.toggle-prefix",
                            Map.of("dimension", dimDisplay))),
                    NameTranslator.translatable(materialName).color(NamedTextColor.YELLOW),
                    MessageUtil.parse(MessageUtil.format(result ? "generator.toggle-enabled" : "generator.toggle-disabled"))
            ));
        }
        return true;
    }

    /**
     * Tab 补全：第二参数补维度，第三参数补矿石名或 all（排除默认产物），第四参数补开关值。
     */
    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            List<String> result = new ArrayList<>();
            for (String dim : DIMENSIONS) {
                if (dim.startsWith(prefix)) result.add(dim);
            }
            return result;
        }

        if (args.length == 3) {
            Optional<Island> islandOpt = getIsland(player);
            if (islandOpt.isEmpty()) return List.of();

            Island island = islandOpt.get();
            String dimension = args[1].toLowerCase();
            if (!DIMENSIONS.contains(dimension)) return List.of();

            GeneratorConfigManager.GeneratorTier tier = plugin.getGeneratorConfigManager()
                    .getTier(island.getGeneratorLevel());
            Map<String, Double> rates = getDimensionRates(tier, dimension);
            String defaultOre = DIMENSION_DEFAULT_ORE.get(dimension);

            String prefix = args[2];
            String upperPrefix = prefix.toUpperCase();
            List<String> result = new ArrayList<>();
            if ("all".startsWith(upperPrefix)) {
                result.add("all");
            }
            for (String material : rates.keySet()) {
                // 排除默认产物（不可禁用）
                if (material.equals(defaultOre)) continue;
                if (material.toUpperCase().startsWith(upperPrefix)) {
                    result.add(material);
                }
            }
            return result;
        }

        if (args.length == 4) {
            String prefix = args[3].toLowerCase();
            List<String> result = new ArrayList<>();
            for (String val : TOGGLE_VALUES) {
                if (val.startsWith(prefix)) result.add(val);
            }
            return result;
        }

        return List.of();
    }

    /**
     * 将用户输入的矿石名（支持中文名和 Material 枚举名）解析为 Material 名。
     *
     * @return 对应的 Material 名，若未匹配则返回 null
     */
    private String resolveOreName(String input, Map<String, Double> rates) {
        String upper = input.toUpperCase();
        if (rates.containsKey(upper)) return upper;
        return null;
    }

    /** 展示发电机状态：列出指定维度（或全部维度）下各产物的权重百分比与启停标记。 */
    private void showGeneratorStatus(Player player, Island island, String targetDim) {
        GeneratorConfigManager.GeneratorTier tier = plugin.getGeneratorConfigManager()
                .getTier(island.getGeneratorLevel());

        MessageUtil.send(player, "generator.status.header", Map.of("level", island.getGeneratorLevel()));

        for (String dim : DIMENSIONS) {
            if (targetDim != null && !dim.equals(targetDim)) continue;

            Map<String, Double> rates = getDimensionRates(tier, dim);
            if (rates.isEmpty()) continue;

            Map<String, Boolean> enabledMap = buildEnabledMap(island, dim, rates);

            String display = DIMENSION_DISPLAY.getOrDefault(dim, dim);
            MessageUtil.send(player, "generator.status.dimension", Map.of("dimension", dim, "display", display));

            double totalWeight = rates.values().stream().mapToDouble(Double::doubleValue).sum();

            for (Map.Entry<String, Double> entry : rates.entrySet()) {
                String material = entry.getKey();
                double weight = entry.getValue();
                double pct = totalWeight > 0 ? (weight * 100.0 / totalWeight) : 0;
                boolean enabled = enabledMap.getOrDefault(material, true);

                if (!MessageUtil.isSilent(player.getUniqueId())) {
                    ((Audience) player).sendMessage(Component.textOfChildren(
                            Component.text("  "),
                            NameTranslator.translatable(material).color(NamedTextColor.YELLOW),
                            Component.text(": ", NamedTextColor.GRAY),
                            Component.text(String.format("%.1f%%", pct), NamedTextColor.GRAY),
                            Component.text("  "),
                            enabled ? Component.text("✓", NamedTextColor.GREEN) : Component.text("✗", NamedTextColor.RED)
                    ));
                }
            }
        }

        MessageUtil.send(player, "generator.usage.help");
    }

    /** 根据岛屿禁用集合构建产物 -> 是否启用的映射。 */
    private Map<String, Boolean> buildEnabledMap(Island island, String dim, Map<String, Double> rates) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        Set<String> disabled = island.getDisabledGeneratorOres().get(dim);
        for (String ore : rates.keySet()) {
            result.put(ore, disabled == null || !disabled.contains(ore));
        }
        return result;
    }

    /** 按维度 key 从发电机档位中取出对应的产物权重表。 */
    private Map<String, Double> getDimensionRates(GeneratorConfigManager.GeneratorTier tier, String dimension) {
        return switch (dimension) {
            case "normal" -> tier.normal();
            case "end" -> tier.end();
            case "nether" -> tier.nether();
            default -> Map.of();
        };
    }
}
