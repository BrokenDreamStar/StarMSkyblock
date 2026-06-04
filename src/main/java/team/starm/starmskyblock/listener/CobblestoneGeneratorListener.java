package team.starm.starmskyblock.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import team.starm.starmskyblock.config.GeneratorConfigManager;
import team.starm.starmskyblock.generator.GeneratorType;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class CobblestoneGeneratorListener implements Listener {

    private final GeneratorConfigManager generatorConfig;
    private final IslandManager islandManager;
    private final SkyblockWorldManager worldManager;

    public CobblestoneGeneratorListener(GeneratorConfigManager generatorConfig, IslandManager islandManager,
                                        SkyblockWorldManager worldManager) {
        this.generatorConfig = generatorConfig;
        this.islandManager = islandManager;
        this.worldManager = worldManager;
    }

    /**
     * 处理熔岩流动，仅拦截以下两种液体互动：
     * 1. 熔岩流水平触碰水 → 圆石替换为岛屿刷石机配置方块（Y0 以下保留深层变种）
     * 2. 熔岩向下流入水 → Y0 以上不做处理，Y0 以下替换为深板岩
     * 3. 玄武岩生成器（保持已有逻辑）
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!generatorConfig.isEnabled()) return;

        if (event.getBlock().getType() != Material.LAVA) return;

        Block fromBlock = event.getBlock();
        Block toBlock = event.getToBlock();
        if (toBlock.getType().isSolid()) return;

        World world = toBlock.getWorld();
        if (!worldManager.isSkyblockWorld(world)) return;

        String worldName = world.getName();
        boolean isSource = isLavaSource(fromBlock);

        // —— 熔岩与水互动 ——
        if (toBlock.getType() == Material.WATER) {
            // 情况 2：熔岩向下流入水 → 石头 / 深板岩
            if (fromBlock.getY() > toBlock.getY()) {
                if (worldManager.isNormalWorld(worldName)
                        && generatorConfig.isDeepslateEnabled()
                        && toBlock.getY() < generatorConfig.getDeepslateYThreshold()) {
                    event.setCancelled(true);
                    toBlock.setType(Material.DEEPSLATE);
                    if (!isSource) {
                        fromBlock.setType(Material.AIR);
                    }
                }
                // Y >= 0：不做任何处理，交给原版生成石头
                return;
            }

            // 情况 1：熔岩流水平触碰水 → 刷石机产物
            if (fromBlock.getY() == toBlock.getY() && !isSource) {
                handleCobblestoneGeneration(event, fromBlock, toBlock, worldName);
                return;
            }

            // 熔岩源水平触碰水 → 黑曜石（原版处理，不拦截）
            return;
        }

        // —— 玄武岩生成器（仅下界生效，主世界/末地使用原版玄武岩生成逻辑） ——
        GeneratorType genType = GeneratorType.detect(toBlock);
        if (genType != GeneratorType.BASALT) return;
        if (!worldManager.isNetherWorld(worldName)) return;

        Optional<Island> islandOpt = islandManager.getIslandAt(
                toBlock.getX() >> 4, toBlock.getZ() >> 4);
        if (islandOpt.isEmpty()) return;

        Island island = islandOpt.get();
        Material selected = selectGeneratorMaterial(island, worldName, toBlock.getLocation());
        if (selected == null) return;

        event.setCancelled(true);
        toBlock.setType(selected);
    }

    /**
     * 安全网：处理 onBlockFromTo 未拦截到的玄武岩 / 圆石生成。
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (!generatorConfig.isEnabled()) return;

        Block block = event.getBlock();
        World world = block.getWorld();
        if (!worldManager.isSkyblockWorld(world)) return;

        Material newType = event.getNewState().getType();
        String worldName = world.getName();

        if (!isGeneratorProduct(newType, worldName)) return;

        Location loc = block.getLocation();
        Optional<Island> islandOpt = islandManager.getIslandAt(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        if (islandOpt.isEmpty()) return;

        Island island = islandOpt.get();
        Material selected = selectGeneratorMaterial(island, worldName, loc);
        if (selected == null || selected == newType) return;

        event.setCancelled(true);
        block.setType(selected);
    }

    // ======================== 刷石机生成逻辑 ========================

    /**
     * 熔岩流水平触碰水 → 圆石替换为刷石机产物。
     * 产物始终放在熔岩流位置，深层变种由 applyDeepslateReplacement 按 Y 值转换。
     */
    private void handleCobblestoneGeneration(BlockFromToEvent event, Block fromBlock, Block toBlock,
                                             String worldName) {
        Optional<Island> islandOpt = islandManager.getIslandAt(
                toBlock.getX() >> 4, toBlock.getZ() >> 4);
        if (islandOpt.isEmpty()) return;

        Island island = islandOpt.get();
        Material selected = selectGeneratorMaterial(island, worldName, fromBlock.getLocation());
        if (selected == null) return;

        event.setCancelled(true);
        fromBlock.setType(selected);
    }

    // ======================== 辅助方法 ========================

    /**
     * 根据岛屿生成器等级和维度，按权重随机选择生成方块。
     */
    private Material selectGeneratorMaterial(Island island, String worldName, Location loc) {
        GeneratorConfigManager.GeneratorTier tier = generatorConfig.getTier(island.getGeneratorLevel());
        Map<String, Double> rates = getDimensionRates(tier, worldName);
        if (rates.isEmpty()) return null;

        Material selected = selectMaterial(rates);
        if (selected == null) return null;

        Material defaultBlock = getDimensionDefaultBlock(worldName);
        selected = applyGeneratorToggle(selected, island, worldName, defaultBlock);
        selected = applyDeepslateReplacement(selected, loc, worldName);
        return selected;
    }

    private boolean isGeneratorProduct(Material type, String worldName) {
        if (type == Material.COBBLESTONE) {
            return worldManager.isNormalWorld(worldName) || worldManager.isEndWorld(worldName);
        }
        if (type == Material.BASALT) {
            return worldManager.isNetherWorld(worldName);
        }
        return false;
    }

    private Map<String, Double> getDimensionRates(GeneratorConfigManager.GeneratorTier tier, String worldName) {
        if (worldManager.isNormalWorld(worldName)) {
            return tier.normal();
        } else if (worldManager.isEndWorld(worldName)) {
            return tier.end();
        } else if (worldManager.isNetherWorld(worldName)) {
            return tier.nether();
        }
        return Map.of();
    }

    private Material selectMaterial(Map<String, Double> rates) {
        double totalWeight = 0;
        for (double w : rates.values()) {
            totalWeight += w;
        }
        if (totalWeight <= 0) return null;

        double random = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0;
        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            cumulative += entry.getValue();
            if (random < cumulative) {
                return Material.matchMaterial(entry.getKey());
            }
        }
        return null;
    }

    private Material applyGeneratorToggle(Material selected, Island island, String worldName, Material defaultBlock) {
        if (island.isGeneratorOreDisabled(getDimensionKey(worldName), selected.name())) {
            return defaultBlock;
        }
        return selected;
    }

    private String getDimensionKey(String worldName) {
        if (worldManager.isNormalWorld(worldName)) return "normal";
        if (worldManager.isEndWorld(worldName)) return "end";
        if (worldManager.isNetherWorld(worldName)) return "nether";
        return "normal";
    }

    private boolean isLavaSource(Block block) {
        if (block.getBlockData() instanceof Levelled levelled) {
            return levelled.getLevel() == 0;
        }
        return false;
    }

    private Material getDimensionDefaultBlock(String worldName) {
        if (worldManager.isNetherWorld(worldName)) return Material.BASALT;
        if (worldManager.isEndWorld(worldName)) return Material.END_STONE;
        return Material.COBBLESTONE;
    }

    private Material applyDeepslateReplacement(Material selected, Location loc, String worldName) {
        if (!worldManager.isNormalWorld(worldName)) return selected;
        if (!generatorConfig.isDeepslateEnabled()) return selected;
        if (loc.getBlockY() >= generatorConfig.getDeepslateYThreshold()) return selected;

        return switch (selected) {
            case COBBLESTONE -> Material.COBBLED_DEEPSLATE;
            case STONE -> Material.DEEPSLATE;
            case COAL_ORE -> Material.DEEPSLATE_COAL_ORE;
            case IRON_ORE -> Material.DEEPSLATE_IRON_ORE;
            case COPPER_ORE -> Material.DEEPSLATE_COPPER_ORE;
            case GOLD_ORE -> Material.DEEPSLATE_GOLD_ORE;
            case DIAMOND_ORE -> Material.DEEPSLATE_DIAMOND_ORE;
            case EMERALD_ORE -> Material.DEEPSLATE_EMERALD_ORE;
            case LAPIS_ORE -> Material.DEEPSLATE_LAPIS_ORE;
            case REDSTONE_ORE -> Material.DEEPSLATE_REDSTONE_ORE;
            default -> selected;
        };
    }
}
