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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 刷石机生成监听器
 * <p>
 * 拦截熔岩/水流动与方块生成事件，将原版圆石/玄武岩/石头产物替换为岛屿刷石机配置表中的加权随机方块。
 * 根据岛屿所在维度（主世界/下界/末地）选取对应的产物列表，并对 Y0 以下方块应用深层岩变种替换。
 * 监听两类事件：
 * <ul>
 *   <li>{@link BlockFromToEvent}：熔岩流向水/玄武岩生成器的主动拦截</li>
 *   <li>{@link BlockFormEvent}：兜底拦截未在 from-to 阶段处理的圆石/玄武岩生成</li>
 * </ul>
 * </p>
 */
public class CobblestoneGeneratorListener implements Listener {

    /** 刷石机配置，提供各等级各维度的加权产物表与深层岩开关 */
    private final GeneratorConfigManager generatorConfig;
    /** 岛屿管理器，根据位置查询岛屿及其刷石机等级 */
    private final IslandManager islandManager;
    /** 世界管理器，判定事件位置所在维度 */
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
        List<GeneratorConfigManager.WeightedEntry> entries = getDimensionEntries(tier, worldName);
        if (entries.isEmpty()) return null;

        Material selected = selectMaterialFast(entries);
        if (selected == null) return null;

        Material defaultBlock = getDimensionDefaultBlock(worldName);
        selected = applyGeneratorToggle(selected, island, worldName, defaultBlock);
        selected = applyDeepslateReplacement(selected, loc, worldName);
        return selected;
    }

    /** 判断生成的方块类型是否属于本监听器应替换的刷石机产物（主世界/末地的圆石、下界的玄武岩） */
    private boolean isGeneratorProduct(Material type, String worldName) {
        if (type == Material.COBBLESTONE) {
            return worldManager.isNormalWorld(worldName) || worldManager.isEndWorld(worldName);
        }
        if (type == Material.BASALT) {
            return worldManager.isNetherWorld(worldName);
        }
        return false;
    }

    /** 根据维度从刷石机等级配置中取出对应的加权产物条目列表 */
    private List<GeneratorConfigManager.WeightedEntry> getDimensionEntries(GeneratorConfigManager.GeneratorTier tier, String worldName) {
        if (worldManager.isNormalWorld(worldName)) {
            return tier.normalEntries();
        } else if (worldManager.isEndWorld(worldName)) {
            return tier.endEntries();
        } else if (worldManager.isNetherWorld(worldName)) {
            return tier.netherEntries();
        }
        return List.of();
    }

    /**
     * 按累计权重随机选取产物。
     * <p>条目列表已按累计权重升序排列，对随机值做二分查找定位首个累计权重大于等于随机的条目。
     * 用 {@link Collections#binarySearch} 替代线性遍历，产物表较大时从 O(n) 降到 O(log n)。</p>
     */
    private Material selectMaterialFast(List<GeneratorConfigManager.WeightedEntry> entries) {
        if (entries.isEmpty()) return null;
        double totalWeight = entries.get(entries.size() - 1).cumulativeWeight();
        if (totalWeight <= 0) return null;
        double random = ThreadLocalRandom.current().nextDouble(totalWeight);
        int idx = Collections.binarySearch(entries,
                new GeneratorConfigManager.WeightedEntry(random, ""),
                Comparator.comparingDouble(GeneratorConfigManager.WeightedEntry::cumulativeWeight));
        if (idx < 0) idx = -idx - 1;
        idx = Math.min(idx, entries.size() - 1);
        return Material.matchMaterial(entries.get(idx).material());
    }

    /** 若岛屿在该维度禁用了选中的矿石，则回退为维度默认方块（玩家手动关闭特定矿石生成） */
    private Material applyGeneratorToggle(Material selected, Island island, String worldName, Material defaultBlock) {
        if (island.isGeneratorOreDisabled(getDimensionKey(worldName), selected.name())) {
            return defaultBlock;
        }
        return selected;
    }

    /** 将世界名映射为刷石机配置使用的维度键字符串（normal/end/nether） */
    private String getDimensionKey(String worldName) {
        if (worldManager.isNormalWorld(worldName)) return "normal";
        if (worldManager.isEndWorld(worldName)) return "end";
        if (worldManager.isNetherWorld(worldName)) return "nether";
        return "normal";
    }

    /** 判断方块是否为 level=0 的熔岩源（流熔岩 level>0 不视为源，避免触发黑曜石路径） */
    private boolean isLavaSource(Block block) {
        if (block.getBlockData() instanceof Levelled levelled) {
            return levelled.getLevel() == 0;
        }
        return false;
    }

    /** 各维度的默认回退方块：下界玄武岩、末地末地石、主世界圆石 */
    private Material getDimensionDefaultBlock(String worldName) {
        if (worldManager.isNetherWorld(worldName)) return Material.BASALT;
        if (worldManager.isEndWorld(worldName)) return Material.END_STONE;
        return Material.COBBLESTONE;
    }

    /**
     * 主世界 Y0 以下将普通矿石/石头替换为深层岩变种。
     * <p>仅主世界生效；下界/末地原样返回。替换表覆盖圆石、石头及各类矿石。</p>
     */
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
