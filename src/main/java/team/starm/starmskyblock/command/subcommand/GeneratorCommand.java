package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.GeneratorConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.permission.IslandPermission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import team.starm.starmskyblock.util.OreDisplayName;

public class GeneratorCommand extends SubCommand {

    private static final List<String> DIMENSIONS = List.of("normal", "nether", "end");
    private static final List<String> TOGGLE_VALUES = List.of("true", "false", "toggle");
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

    @Override
    public boolean execute(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 4, "/is generator [维度] [矿石] [true/false/toggle]")) return true;

        Optional<Island> islandOpt = getIsland(player);
        if (islandOpt.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
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
                MessageUtil.sendMessage(player, "&c无效的维度！可选: normal, end, nether");
                return true;
            }
            showGeneratorStatus(player, island, dimension);
            return true;
        }

        // /is generator <维度> all <true/false/toggle> — 批量切换所有矿石
        if (args[2].equalsIgnoreCase("all")) {
            if (!island.hasPermission(player.getUniqueId(), IslandPermission.SET_GENERATOR)) {
                MessageUtil.sendMessage(player, "&c你没有权限管理刷石机！");
                return true;
            }

            GeneratorConfigManager.GeneratorTier tier = plugin.getGeneratorConfigManager()
                    .getTier(island.getGeneratorLevel());
            Map<String, Double> rates = getDimensionRates(tier, dimension);
            if (rates.isEmpty()) {
                MessageUtil.sendMessage(player, "&c该维度没有可配置的矿石！");
                return true;
            }

            String defaultOre = DIMENSION_DEFAULT_ORE.get(dimension);

            int toggled = 0;
            if (args.length < 4) {
                MessageUtil.sendMessage(player, "&c用法: /is generator " + dimension + " all <true/false>");
                return true;
            }
            Boolean enableAll;
            if (args[3].equalsIgnoreCase("true")) {
                enableAll = Boolean.TRUE;
            } else if (args[3].equalsIgnoreCase("false")) {
                enableAll = Boolean.FALSE;
            } else {
                MessageUtil.sendMessage(player, "&c无效的值！可选: true, false");
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
            String statusDisplay = enableAll ? "已全部启用" : "已全部禁用";
            MessageUtil.sendMessage(player, "&a已将 " + dimDisplay + " 的 " + toggled + " 种矿石" + statusDisplay);
            return true;
        }

        // /is generator <维度> <矿石> <true/false/toggle> — 切换单个矿石
        if (!island.hasPermission(player.getUniqueId(), IslandPermission.SET_GENERATOR)) {
            MessageUtil.sendMessage(player, "&c你没有权限管理刷石机！");
            return true;
        }

        GeneratorConfigManager.GeneratorTier tier = plugin.getGeneratorConfigManager()
                .getTier(island.getGeneratorLevel());
        Map<String, Double> rates = getDimensionRates(tier, dimension);

        String materialName = resolveOreName(args[2], rates);
        if (materialName == null) {
            MessageUtil.sendMessage(player, "&c该矿石不存在于当前等级的 " + dimension + " 维度的刷石机概率表中！");
            return true;
        }

        // 禁止禁用维度默认产物
        String defaultOre = DIMENSION_DEFAULT_ORE.get(dimension);
        if (materialName.equals(defaultOre)) {
            String defaultDisplay = OreDisplayName.toChinese(defaultOre);
            MessageUtil.sendMessage(player, "&c" + defaultDisplay + " 是 " + dimension + " 维度的默认产物，无法禁用！");
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
                MessageUtil.sendMessage(player, "&c无效的值！可选: true, false, toggle");
                return true;
            }
        }

        boolean result = island.toggleGeneratorOre(dimension, materialName, enable);
        String json = island.getDisabledGeneratorOresJson();
        plugin.getIslandManager().updateIslandGeneratorDisabled(island.getId(), json);

        String displayName = OreDisplayName.toChinese(materialName);
        String status = result ? "&a启用" : "&c禁用";
        MessageUtil.sendMessage(player, "&a刷石机 " + dimension + " 维度的 " + displayName + " 已" + status);
        return true;
    }

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
                String chinese = OreDisplayName.toChinese(material);
                if (chinese.startsWith(prefix) || material.toUpperCase().startsWith(upperPrefix)) {
                    result.add(chinese);
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
        String fromChinese = OreDisplayName.toMaterial(input);
        if (fromChinese != null && rates.containsKey(fromChinese)) return fromChinese;
        return null;
    }

    private void showGeneratorStatus(Player player, Island island, String targetDim) {
        GeneratorConfigManager.GeneratorTier tier = plugin.getGeneratorConfigManager()
                .getTier(island.getGeneratorLevel());

        MessageUtil.sendMessage(player, "&a=== 刷石机状态 &7(等级: &e" + island.getGeneratorLevel() + "&7) ===");

        for (String dim : DIMENSIONS) {
            if (targetDim != null && !dim.equals(targetDim)) continue;

            Map<String, Double> rates = getDimensionRates(tier, dim);
            if (rates.isEmpty()) continue;

            Map<String, Boolean> enabledMap = buildEnabledMap(island, dim, rates);

            String display = DIMENSION_DISPLAY.getOrDefault(dim, dim);
            MessageUtil.sendMessage(player, "&b▶ " + dim + " &7(" + display + ")");

            double totalWeight = rates.values().stream().mapToDouble(Double::doubleValue).sum();

            for (Map.Entry<String, Double> entry : rates.entrySet()) {
                String material = entry.getKey();
                double weight = entry.getValue();
                double pct = totalWeight > 0 ? (weight * 100.0 / totalWeight) : 0;
                boolean enabled = enabledMap.getOrDefault(material, true);

                String displayName = OreDisplayName.toChinese(material);

                String statusIcon = enabled ? "&a✓" : "&c✗";
                String line = String.format("  &e%s&7: %.1f%%  %s",
                        displayName, pct, statusIcon);

                MessageUtil.sendMessage(player, line);
            }
        }

        MessageUtil.sendMessage(player, "&7使用 &e/is generator <维度> <矿石> <true/false/toggle> &7设置矿石是否生成");
    }

    private Map<String, Boolean> buildEnabledMap(Island island, String dim, Map<String, Double> rates) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        Set<String> disabled = island.getDisabledGeneratorOres().get(dim);
        for (String ore : rates.keySet()) {
            result.put(ore, disabled == null || !disabled.contains(ore));
        }
        return result;
    }

    private Map<String, Double> getDimensionRates(GeneratorConfigManager.GeneratorTier tier, String dimension) {
        return switch (dimension) {
            case "normal" -> tier.normal();
            case "end" -> tier.end();
            case "nether" -> tier.nether();
            default -> Map.of();
        };
    }
}
