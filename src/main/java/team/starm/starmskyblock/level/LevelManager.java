package team.starm.starmskyblock.level;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.AuraSkillsContributionConfig;
import team.starm.starmskyblock.config.ExperienceConfig;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.integration.AuraSkillsIntegration;
import team.starm.starmskyblock.integration.AuraSkillsIslandResult;
import team.starm.starmskyblock.integration.MemberSkillData;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 岛屿等级管理器 —— 负责等级计算的协调、冷却控制和结果持久化。
 * <p>
 * 当玩家执行 {@code /is level} 时：
 * <ol>
 *   <li>检查冷却，未冷却则创建 {@link IslandLevelCalculator} 开始异步扫描</li>
 *   <li>扫描完成后将结果写入数据库和内存缓存</li>
 *   <li>向玩家发送结果消息</li>
 * </ol>
 */
public class LevelManager {

    private static final int CHUNKS_PER_TICK = 16;

    private final StarMSkyblock plugin;
    private final ExperienceConfig experienceConfig;
    private final AuraSkillsContributionConfig auraskillsConfig;
    private final IslandManager islandManager;
    private final SkyblockWorldManager worldManager;

    /**
     * 玩家冷却 Map（玩家 UUID → 上次触发时间戳）
     */
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public LevelManager(StarMSkyblock plugin, ExperienceConfig experienceConfig,
                        AuraSkillsContributionConfig auraskillsConfig,
                        IslandManager islandManager, SkyblockWorldManager worldManager) {
        this.plugin = plugin;
        this.experienceConfig = experienceConfig;
        this.auraskillsConfig = auraskillsConfig;
        this.islandManager = islandManager;
        this.worldManager = worldManager;
    }

    /**
     * 触发岛屿等级计算。
     *
     * @param island 目标岛屿
     * @param player 请求计算的玩家（用于发送消息）
     */
    public void calculateIsland(Island island, Player player) {
        UUID ownerId = island.getOwnerId();

        // 冷却检查（从 config.yml 读取，设为 0 表示无冷却）
        int cooldownSeconds = plugin.getConfigManager().getLevelCooldown();
        if (cooldownSeconds > 0) {
            long now = System.currentTimeMillis();
            Long lastCalc = cooldowns.get(ownerId);
            if (lastCalc != null) {
                long cooldownMs = cooldownSeconds * 1000L;
                long elapsed = now - lastCalc;
                if (elapsed < cooldownMs) {
                    long remaining = cooldownSeconds - (elapsed / 1000);
                    MessageUtil.sendMessage(player, "&c等级计算冷却中，请等待 &e" + remaining + " &c秒后再试");
                    return;
                }
            }
            cooldowns.put(ownerId, now);
        }

        MessageUtil.sendMessage(player, "&a开始计算岛屿等级，请稍候...");

        IslandLevelCalculator calculator = new IslandLevelCalculator(
                plugin, island, worldManager, experienceConfig, this,
                (calculatedIsland, results) -> {
                    // 主线程回调：持久化结果 + 发消息
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        onCalculationComplete(calculatedIsland, results, player);
                    });
                });

        calculator.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * 由 {@link IslandLevelCalculator} 调用的进度更新
     */
    public void sendProgress(UUID ownerId, int done, int total) {
        // 可选：通过 ActionBar 发送进度
        // 暂时不实现，避免刷屏
    }

    /**
     * 计算完成后的处理
     */
    private void onCalculationComplete(Island island, LevelResults results, Player player) {
        int oldLevel = island.getLevel();
        int blockLevel = results.getLevel();

        // 先更新方块扫描结果到内存缓存（不含 AuraSkills 加成）
        island.setLevel(blockLevel);
        island.setExperience(results.getTotalExperience());
        island.setBlockCounts(results.getBlockCounts());

        // 如果 AuraSkills 可用且功能启用，异步计算加成等级
        if (AuraSkillsIntegration.isAvailable() && auraskillsConfig.isEnabled()) {
            int finalBlockLevel = blockLevel;
            AuraSkillsIntegration.getIslandResult(island).thenAccept(result -> {
                double coefficient = auraskillsConfig.getCoefficient();
                int rawBonus = (int) (result.getTotalPowerLevel() / coefficient);
                int maxBonus = auraskillsConfig.getMaxBonusLevel();
                int bonus = maxBonus > 0 && rawBonus > maxBonus ? maxBonus : rawBonus;

                results.setAuraSkillsContribution(bonus);
                results.setTotalPowerLevel(result.getTotalPowerLevel());
                results.setCoefficient(coefficient);
                results.setMemberSkillData(result.getMemberData());

                int finalLevel = finalBlockLevel + bonus;
                island.setLevel(finalLevel);
                results.setLevel(finalLevel);
                island.setAuraSkillsContribution(bonus);

                // 确保回到主线程进行数据库写入和消息发送
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    islandManager.getIslandRepository().updateLevel(
                            island.getId(), finalLevel, results.getTotalExperience(),
                            serializeBlockCounts(results.getBlockCounts()), bonus
                    );
                    sendLevelResults(player, island, results, oldLevel, finalBlockLevel);
                });
            });
        } else {
            // 无 AuraSkills：直接持久化并发送结果
            islandManager.getIslandRepository().updateLevel(
                    island.getId(), blockLevel, results.getTotalExperience(),
                    serializeBlockCounts(results.getBlockCounts())
            );
            sendLevelResults(player, island, results, oldLevel, blockLevel);
        }
    }

    /**
     * 发送等级计算结果
     */
    private void sendLevelResults(Player player, Island island, LevelResults results, int oldLevel, int blockLevel) {
        int levelChange = results.getLevel() - oldLevel;

        // ===== 等级概览 =====
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&a=== 岛屿等级计算完成 ===");
        MessageUtil.sendMessage(player, "&d岛屿等级: &f" + oldLevel + " &7→ &d" + results.getLevel()
                + (levelChange > 0 ? " &7(&a+" + levelChange + "&7)" : ""));

        // ===== 方块信息 =====
        MessageUtil.sendMessage(player, "&a岛屿方块:");
        MessageUtil.sendMessage(player, "  &a方块等级: &f" + blockLevel);
        MessageUtil.sendMessage(player, "  &a总经验值: &f" + String.format("%.2f", results.getTotalExperience()));

        // 计算升到下一级所需经验值
        if (experienceConfig.hasLevelCost()) {
            double base = experienceConfig.getLevelExpBase();
            double power = experienceConfig.getLevelExpPower();
            int currentLevel = results.getLevel();
            long totalForNext = 0;
            for (int i = 1; i <= currentLevel + 1; i++) {
                totalForNext += Math.round(base * Math.pow(i, power));
            }
            long needed = Math.max(0, totalForNext - (long) results.getTotalExperience());
            MessageUtil.sendMessage(player, "  &a升到下级所需: &f" + needed);
        }

        MessageUtil.sendMessage(player, "  &a有效方块数: &f" + results.getBlocksCounted());

        // 显示超阈值信息（使用 TranslatableComponent 显示方块译名）
        if (!results.getBlocksOverLimit().isEmpty()) {
            TextComponent.Builder builder = Component.text()
                    .append(Component.text("  超出阈值的方块: ", NamedTextColor.GREEN));
            for (Map.Entry<Material, Long> entry : results.getBlocksOverLimit().entrySet()) {
                if (entry.getValue() > 0) {
                    builder.append(Component.text(" ", NamedTextColor.WHITE))
                            .append(Component.translatable(entry.getKey().getItemTranslationKey()))
                            .append(Component.text("(", NamedTextColor.WHITE))
                            .append(Component.text(String.valueOf(entry.getValue()), NamedTextColor.RED))
                            .append(Component.text(") ", NamedTextColor.WHITE));
                }
            }
            MessageUtil.sendMessage(player, builder.build());
        }

        MessageUtil.sendMessage(player, "  &a扫描区块: &f" + results.getTotalChunksScanned() + " (" + results.getWorldsScanned() + " 个世界)");
        long timeMs = results.getTimeTaken() / 1_000_000;
        String timeStr = timeMs < 1000
                ? timeMs + "ms"
                : String.format("%.2fs", timeMs / 1000.0);
        MessageUtil.sendMessage(player, "  &a耗时: &f" + timeStr);

        // ===== 额外加成（AuraSkills）移至底部 =====
        if (results.getAuraSkillsContribution() > 0) {
            MessageUtil.sendMessage(player, "&b额外加成(AuraSkills):");
            double coeff = results.getCoefficient();
            for (MemberSkillData member : results.getMemberSkillData()) {
                int memberContribution = (int) (member.powerLevel() / coeff);
                if (memberContribution > 0 || member.powerLevel() > 0) {
                    MessageUtil.sendMessage(player, "  &f" + member.playerName()
                            + "&7(Lv." + member.powerLevel() + ")"
                            + "&b→" + memberContribution);
                }
            }
        }

        MessageUtil.sendMessage(player, "");
    }

    /**
     * 获取缓存的岛屿等级
     */
    public int getCachedLevel(Island island) {
        return island.getLevel();
    }

    /**
     * 获取缓存的岛屿经验值
     */
    public double getCachedExperience(Island island) {
        return island.getExperience();
    }

    public ExperienceConfig getExperienceConfig() {
        return experienceConfig;
    }

    // ==================== 模板基线 ====================

    /**
     * 保存岛屿的模板基线（扫描 Schematic 中的方块作为 baseline）。
     * 在岛屿创建完成、三个世界的结构都粘贴完毕后调用。
     * <p>
     * 直接从 SchematicManager 缓存的 Clipboard 读取方块，
     * 纯内存操作，无需加载区块。
     *
     * @param island      已创建好的岛屿
     * @param schematicId 模板 ID（如 "default"）
     */
    public void saveBaseline(Island island, String schematicId) {
        ConfigManager configManager = plugin.getConfigManager();
        var schematicManager = plugin.getSchematicManager();

        String[] schematics = {
                configManager.getNormalSchematicFileName(schematicId),
                configManager.getNetherSchematicFileName(schematicId),
                configManager.getEndSchematicFileName(schematicId)
        };

        Map<String, Long> baselineCounts = new HashMap<>();

        for (String fileName : schematics) {
            Clipboard clipboard = schematicManager.getSchematic(fileName);
            if (clipboard == null) {
                MessageUtil.consoleWarn("无法获取模板文件计算基线: " + fileName);
                continue;
            }

            for (com.sk89q.worldedit.math.BlockVector3 pos : clipboard.getRegion()) {
                com.sk89q.worldedit.world.block.BlockState blockState = clipboard.getBlock(pos);
                com.sk89q.worldedit.world.block.BlockType blockType = blockState.getBlockType();

                if (blockType == null || blockType.equals(BlockTypes.AIR)) {
                    continue;
                }

                Material material = BukkitAdapter.adapt(blockType);
                if (material == null || material.isAir()) {
                    continue;
                }

                baselineCounts.merge(material.name(), 1L, Long::sum);
            }
        }

        island.setBaselineBlockCounts(baselineCounts);

        // 计算基线总分
        double totalBaselineExperience = 0;
        for (Map.Entry<String, Long> entry : baselineCounts.entrySet()) {
            Material material = Material.getMaterial(entry.getKey());
            if (material != null) {
                totalBaselineExperience += experienceConfig.getExperience(material) * entry.getValue();
            }
        }
        island.setBaselineExperience(totalBaselineExperience);

        // 持久化到数据库
        islandManager.getIslandRepository().updateBaseline(
                island.getId(),
                totalBaselineExperience,
                serializeStringMap(baselineCounts)
        );

        MessageUtil.consolePrint("岛屿 #" + island.getId() + " 模板基线已保存（" + baselineCounts.size() + " 种方块，共 " + totalBaselineExperience + " 经验值）");
    }

    // ==================== 序列化辅助 ====================

    /**
     * 将 String→Long Map 序列化为 JSON 字符串（用于模板基线）
     */
    private String serializeStringMap(Map<String, Long> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            if (!first) json.append(",");
            first = false;
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
        }
        json.append("}");
        return json.toString();
    }

    /**
     * 将方块计数 Map 序列化为 JSON 字符串
     */
    private String serializeBlockCounts(Map<Material, Long> blockCounts) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<Material, Long> entry : blockCounts.entrySet()) {
            if (!first) json.append(",");
            first = false;
            json.append("\"").append(entry.getKey().name()).append("\":").append(entry.getValue());
        }
        json.append("}");
        return json.toString();
    }
}