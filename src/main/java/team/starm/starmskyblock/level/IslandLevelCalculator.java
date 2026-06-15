package team.starm.starmskyblock.level;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.ExperienceConfig;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * 岛屿等级计算器 —— 异步扫描岛屿全境方块并计算等级。
 * <p>
 * 扫描流程：
 * <ol>
 *   <li>根据岛屿中心坐标和半径计算所有区块坐标</li>
 *   <li>跨主世界/下界/末地三个世界分批加载区块</li>
 *   <li>分批获取 ChunkSnapshot 后进行异步方块扫描</li>
 *   <li>对每种方块应用阈值限制后累加经验值</li>
 *   <li>通过公式将总经验换算为岛屿等级</li>
 * </ol>
 * <p>
 * 性能优化：每 tick 处理 16 区块，空区块跳过，异步方块计数。
 */
public class IslandLevelCalculator extends BukkitRunnable {

    private static final int BATCH_SIZE = 16;

    private final StarMSkyblock plugin;
    private final Island island;
    private final SkyblockWorldManager worldManager;
    private final ExperienceConfig experienceConfig;
    private final LevelManager levelManager;

    /** 所有世界的区块坐标队列 */
    private final Queue<ChunkPos> chunkQueue = new ArrayDeque<>();
    /** 待处理的世界列表 */
    private final List<World> worlds = new ArrayList<>();
    /** 自定义完成回调 */
    private final BiConsumer<Island, LevelResults> onComplete;

    /** 模板基线方块计数（用于忽略初始模板方块） */
    private final Map<String, Long> baselineBlockCounts;

    /** 原始方块计数（未扣除基线、未应用限制，所有批次汇总后统一处理） */
    private final Map<Material, Long> rawTotalCounts = new HashMap<>();

    /** 异步未完成任务计数 */
    private final AtomicInteger pendingAsyncTasks = new AtomicInteger(0);
    /** 扫描结果 */
    private final LevelResults results = new LevelResults();
    /** 各世界已扫描区块数 */
    private int chunksScanned;

    /** 当前阶段 */
    private Phase phase = Phase.INIT;

    private enum Phase { INIT, LOADING, WAITING, FINISH }

    /**
     * @param island        目标岛屿
     * @param levelManager  等级管理器（用于完成回调）
     * @param onComplete    扫描完成回调（接收岛屿和结果）
     */
    public IslandLevelCalculator(StarMSkyblock plugin, Island island,
                                  SkyblockWorldManager worldManager,
                                  ExperienceConfig experienceConfig,
                                  LevelManager levelManager,
                                  BiConsumer<Island, LevelResults> onComplete) {
        this.plugin = plugin;
        this.island = island;
        this.worldManager = worldManager;
        this.experienceConfig = experienceConfig;
        this.levelManager = levelManager;
        this.onComplete = onComplete;
        this.baselineBlockCounts = new HashMap<>(island.getBaselineBlockCounts());
    }

    @Override
    public void run() {
        switch (phase) {
            case INIT -> initPhase();
            case LOADING -> loadingPhase();
            case WAITING -> waitingPhase();
            case FINISH -> finishPhase();
        }
    }

    // ==================== 阶段 1：计算区块坐标 ====================

    private void initPhase() {
        World normal = worldManager.getOrCreateSkyblockWorld();
        if (normal != null) worlds.add(normal);

        World nether = worldManager.getOrCreateSkyblockNether();
        if (nether != null) worlds.add(nether);

        World end = worldManager.getOrCreateSkyblockEnd();
        if (end != null) worlds.add(end);

        if (worlds.isEmpty()) {
            MessageUtil.consoleError("岛屿等级计算失败：没有可用的世界（ID=" + island.getId() + "）");
            cancel();
            return;
        }

        results.setWorldsScanned(worlds.size());

        int cx = island.getCenterChunkX();
        int cz = island.getCenterChunkZ();
        int radius = island.getRadius();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                chunkQueue.add(new ChunkPos(cx + dx, cz + dz));
            }
        }

        MessageUtil.consolePrint("岛屿 #" + island.getId() + " 计算等级：共 " + chunkQueue.size() + " 个区块位置，"
                + worlds.size() + " 个世界");

        int totalPositions = chunkQueue.size() * worlds.size();
        results.setTotalChunksScanned(totalPositions);

        phase = Phase.LOADING;
    }

    // ==================== 阶段 2：逐批加载 + 提交异步处理 ====================

    private void loadingPhase() {
        // 取下一批区块坐标
        List<ChunkPos> batch = new ArrayList<>();
        while (!chunkQueue.isEmpty() && batch.size() < BATCH_SIZE) {
            batch.add(chunkQueue.poll());
        }

        if (batch.isEmpty()) {
            // 所有区块已出队，等待异步任务完成
            phase = Phase.WAITING;
            return;
        }

        // 加载这批区块并取快照
        List<SnapshotTask> snapshotTasks = new ArrayList<>();
        for (ChunkPos pos : batch) {
            for (World world : worlds) {
                if (!world.isChunkGenerated(pos.x, pos.z)) continue;
                Chunk chunk = world.getChunkAt(pos.x, pos.z);
                int minY = world.getMinHeight();
                int maxY = world.getMaxHeight();
                ChunkSnapshot snapshot = chunk.getChunkSnapshot();
                if (snapshot != null) {
                    snapshotTasks.add(new SnapshotTask(snapshot, pos.x, pos.z, minY, maxY));
                }
            }
        }

        // 提交异步处理
        pendingAsyncTasks.incrementAndGet();
        int remainingPositions = chunkQueue.size() * worlds.size();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            processSnapshots(snapshotTasks, remainingPositions);
            pendingAsyncTasks.decrementAndGet();
        });
    }

    // ==================== 阶段 3：等待异步任务全部完成 ====================

    private void waitingPhase() {
        if (pendingAsyncTasks.get() > 0) {
            return; // 继续等待
        }
        phase = Phase.FINISH;
    }

    // ==================== 阶段 4：计算等级并回调 ====================

    private void finishPhase() {
        long start = System.nanoTime();

        // 扣除模板基线 + 应用方块限制
        for (Map.Entry<Material, Long> entry : rawTotalCounts.entrySet()) {
            Material material = entry.getKey();
            long count = entry.getValue();

            // 扣除模板基线中的方块数量
            long baselineCount = baselineBlockCounts.getOrDefault(material.name(), 0L);
            long adjustedCount = Math.max(0, count - baselineCount);

            if (adjustedCount == 0) continue;

            // 应用阈值限制 + 递减计算
            long limit = experienceConfig.getLimit(material);
            long counted;
            long overLimit = 0;
            boolean diminishingActive = false;

            if (limit != Long.MAX_VALUE && adjustedCount > limit) {
                counted = limit;
                overLimit = adjustedCount - limit;
                diminishingActive = experienceConfig.isDiminishingEnabled() && overLimit > 0;
            } else {
                counted = adjustedCount;
            }

            double expValue = experienceConfig.getExperience(material);
            results.addBlockCount(material, counted);
            double exp = expValue * counted;

            if (diminishingActive && expValue > 0) {
                // 对超额部分应用递减公式：max(value / (1 + decay * i), minimum)
                double decay = experienceConfig.getDiminishingDecay();
                double minimum = experienceConfig.getDiminishingMinimum();
                for (long i = 0; i < overLimit; i++) {
                    exp += Math.round(Math.max(expValue / (1 + decay * i), minimum));
                }
                results.addBlockCount(material, overLimit);
                results.addBlockOverLimit(material, overLimit);
            } else if (overLimit > 0) {
                results.addBlockOverLimit(material, overLimit);
            }

            results.setTotalExperience(results.getTotalExperience() + exp);
            results.setBlocksCounted(results.getBlocksCounted() + counted + (diminishingActive ? overLimit : 0));
        }

        // 计算等级
        double totalExp = results.getTotalExperience();
        int level;

        if (experienceConfig.hasLevelCost()) {
            // 幂函数增长模型：cost(L) = round(base * L^power)
            // 累计：total(L) = Σ round(base * i^power) for i = 1 to L
            // 反推：遍历 L 从 1 递增累加，直到超出 totalExp
            double base = experienceConfig.getLevelExpBase();
            double power = experienceConfig.getLevelExpPower();

            if (totalExp < Math.round(base)) {
                level = 0;
            } else {
                double cumulative = 0;
                int lvl = 0;
                while (true) {
                    lvl++;
                    long cost = Math.round(base * Math.pow(lvl, power));
                    if (cost <= 0) { // 防溢出/无限循环
                        level = lvl - 1;
                        break;
                    }
                    cumulative += cost;
                    if (cumulative > totalExp) {
                        level = lvl - 1;
                        break;
                    }
                }
            }
        } else {
            // 旧版公式回退（兼容 {points} 和 {experience}）
            String expr = experienceConfig.getLevelFormula()
                    .replace("{experience}", String.valueOf((long) totalExp))
                    .replace("{points}", String.valueOf((long) totalExp));
            try {
                level = (int) Math.floor(evaluateSimpleExpression(expr));
            } catch (Exception e) {
                MessageUtil.consoleError("等级公式计算失败: " + expr, e);
                level = (int) (totalExp / 100);
            }
        }

        if (level < 0) level = 0;
        if (level == 0 && results.getBlocksCounted() > 0) level = 1;

        results.setLevel(level);
        results.setTimeTaken(System.nanoTime() - start);

        cancel();
        onComplete.accept(island, results);
    }

    // ==================== 异步：处理一批快照 ====================

    private void processSnapshots(List<SnapshotTask> tasks, int remainingPositions) {
        Map<Material, Long> worldCounts = new HashMap<>();

        for (SnapshotTask task : tasks) {
            ChunkSnapshot snapshot = task.snapshot;
            int minY = task.minY;
            int maxY = task.maxY;

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        Material type = snapshot.getBlockType(x, y, z);
                        if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
                            continue;
                        }
                        worldCounts.merge(type, 1L, Long::sum);
                    }
                }
            }
        }

        // 同步汇总原始计数（不含基线扣除和限制，finishPhase 统一处理）
        synchronized (rawTotalCounts) {
            for (Map.Entry<Material, Long> entry : worldCounts.entrySet()) {
                rawTotalCounts.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
        }

        chunksScanned += tasks.size();

        // 通过 LevelManager 发送进度更新
        int done = results.getTotalChunksScanned() - remainingPositions + chunksScanned;
        int total = results.getTotalChunksScanned();
        levelManager.sendProgress(island.getOwnerId(), done, total);
    }

    // ==================== 简易表达式计算器 ====================

    private static double evaluateSimpleExpression(String expr) {
        expr = expr.trim().replaceAll("\\s+", "");
        return parseAddSub(expr, new int[]{0});
    }

    private static double parseAddSub(String expr, int[] pos) {
        double left = parseMulDiv(expr, pos);
        while (pos[0] < expr.length()) {
            char op = expr.charAt(pos[0]);
            if (op == '+' || op == '-') {
                pos[0]++;
                double right = parseMulDiv(expr, pos);
                left = (op == '+') ? left + right : left - right;
            } else {
                break;
            }
        }
        return left;
    }

    private static double parseMulDiv(String expr, int[] pos) {
        double left = parsePrimary(expr, pos);
        while (pos[0] < expr.length()) {
            char op = expr.charAt(pos[0]);
            if (op == '*' || op == '/') {
                pos[0]++;
                double right = parsePrimary(expr, pos);
                left = (op == '*') ? left * right : left / right;
            } else {
                break;
            }
        }
        return left;
    }

    private static double parsePrimary(String expr, int[] pos) {
        if (pos[0] >= expr.length()) return 0;
        char c = expr.charAt(pos[0]);
        if (c == '(') {
            pos[0]++;
            double val = parseAddSub(expr, pos);
            if (pos[0] < expr.length() && expr.charAt(pos[0]) == ')') pos[0]++;
            return val;
        }
        // 数字
        int start = pos[0];
        while (pos[0] < expr.length() && (Character.isDigit(expr.charAt(pos[0])) || expr.charAt(pos[0]) == '.')) {
            pos[0]++;
        }
        if (start == pos[0]) return 0;
        return Double.parseDouble(expr.substring(start, pos[0]));
    }

    // ==================== 内部类 ====================

    private record ChunkPos(int x, int z) {}

    private record SnapshotTask(ChunkSnapshot snapshot, int chunkX, int chunkZ, int minY, int maxY) {}
}