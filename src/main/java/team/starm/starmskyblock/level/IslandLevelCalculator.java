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
 * 性能优化：通过 Paper 的 getChunkAtAsync 异步加载区块（加载在 Paper 内部线程完成，
 * 不阻塞主线程），空区块跳过，快照在主线程回调取回后提交异步线程做方块计数。
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

    /** 异步方块计数任务计数（processSnapshots 在异步线程执行） */
    private final AtomicInteger pendingAsyncTasks = new AtomicInteger(0);
    /** 飞行中的 getChunkAtAsync 请求数（区块加载在 Paper 异步线程，回调在主线程执行） */
    private final AtomicInteger pendingChunkLoads = new AtomicInteger(0);
    /** 扫描结果 */
    private final LevelResults results = new LevelResults();
    /** 各世界已扫描区块数（processSnapshots 在异步线程累加，用原子类型避免并发丢增量） */
    private final AtomicInteger chunksScanned = new AtomicInteger(0);

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
        // 区块坐标队列已空 -> 进入等待阶段（可能仍有待回调的 getChunkAtAsync / processSnapshots）
        if (chunkQueue.isEmpty()) {
            phase = Phase.WAITING;
            return;
        }

        // 取下一批区块坐标
        List<ChunkPos> batch = new ArrayList<>();
        while (!chunkQueue.isEmpty() && batch.size() < BATCH_SIZE) {
            batch.add(chunkQueue.poll());
        }

        // 展开本批的 (ChunkPos, World) 组合，跳过未生成的区块（避免无谓生成空空气区块）
        List<ChunkPos> positions = new ArrayList<>();
        List<World> targetWorlds = new ArrayList<>();
        for (ChunkPos pos : batch) {
            for (World world : worlds) {
                if (!world.isChunkGenerated(pos.x, pos.z)) continue;
                positions.add(pos);
                targetWorlds.add(world);
            }
        }
        if (positions.isEmpty()) {
            // 本批无可加载位置，下个 tick 继续取下一批
            return;
        }

        // 用 Paper 的 getChunkAtAsync 异步加载区块：加载/生成在 Paper 内部线程完成，
        // 回调在主线程执行（Paper 保证）。原实现 world.getChunkAt(...) 是主线程同步加载，
        // 区块未驻留时会触发磁盘 IO / 区块生成，卡主线程（大半径岛屿可达数秒）。
        // getChunkAtAsync 把加载移出主线程；回调内仅做 getChunkSnapshot（内存拷贝，远轻于加载）。
        // 回调全在主线程，故 snapshotTasks / batchRemaining 的访问无并发问题；
        // 提交 runTaskAsynchronously 时 scheduler 建立 happens-before，异步线程可见全部快照。
        final List<SnapshotTask> snapshotTasks = new ArrayList<>();
        final AtomicInteger batchRemaining = new AtomicInteger(positions.size());
        final int remainingPositions = chunkQueue.size() * worlds.size();

        for (int i = 0; i < positions.size(); i++) {
            final ChunkPos pos = positions.get(i);
            final World world = targetWorlds.get(i);
            pendingChunkLoads.incrementAndGet();
            // getChunkAtAsync(x, z, gen) 返回 CompletableFuture，其完成始终在主线程（Paper 保证），
            // 故 whenComplete 回调在主线程执行，getChunkSnapshot 线程安全。
            // 用 whenComplete 而非 thenAccept：确保即使加载失败（future 异常完成）也递减计数，
            // 否则 waitingPhase 会永久等待、等级计算卡死。gen=true 与原 getChunkAt 语义一致（加载或生成）。
            world.getChunkAtAsync(pos.x, pos.z, true).whenComplete((chunk, ex) -> {
                try {
                    if (ex == null && chunk != null) {
                        ChunkSnapshot snapshot = chunk.getChunkSnapshot();
                        if (snapshot != null) {
                            snapshotTasks.add(new SnapshotTask(snapshot, pos.x, pos.z,
                                    world.getMinHeight(), world.getMaxHeight()));
                        }
                    } else if (ex != null) {
                        MessageUtil.consoleWarn("等级扫描异步加载区块失败: " + world.getName()
                                + " @(" + pos.x + "," + pos.z + "): " + ex.getMessage());
                    }
                } finally {
                    pendingChunkLoads.decrementAndGet();
                    // 本批全部回调完成 -> 提交异步方块计数
                    if (batchRemaining.decrementAndGet() == 0) {
                        pendingAsyncTasks.incrementAndGet();
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            processSnapshots(snapshotTasks, remainingPositions);
                            pendingAsyncTasks.decrementAndGet();
                        });
                    }
                }
            });
        }
    }

    // ==================== 阶段 3：等待异步任务全部完成 ====================

    private void waitingPhase() {
        // 等待所有飞行中的 getChunkAtAsync 回调与异步方块计数任务全部完成
        if (pendingChunkLoads.get() > 0 || pendingAsyncTasks.get() > 0) {
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

            // 扣除模板基线中的方块数量（可在 level.yml 中通过 baseline.enabled 关闭）
            long baselineCount = experienceConfig.isBaselineEnabled()
                    ? baselineBlockCounts.getOrDefault(material.name(), 0L)
                    : 0L;
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
                exp += LevelFormula.diminishingReturns(overLimit, expValue,
                        experienceConfig.getDiminishingDecay(), experienceConfig.getDiminishingMinimum());
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
            // 幂函数增长模型：cost(L) = round(base * L^power)，累计 total(L) = Σ cost(i)，反推等级。
            // 溢出/退化（cost<=0）保护由 LevelFormula 内部处理，详见其单元测试。
            level = LevelFormula.fromPowerCurve(totalExp,
                    experienceConfig.getLevelExpBase(), experienceConfig.getLevelExpPower());
        } else {
            // 旧版公式回退（兼容 {points} 和 {experience}）
            String expr = experienceConfig.getLevelFormula()
                    .replace("{experience}", String.valueOf((long) totalExp))
                    .replace("{points}", String.valueOf((long) totalExp));
            try {
                level = (int) Math.floor(ExpressionParser.evaluate(expr));
            } catch (Exception e) {
                MessageUtil.consoleError("等级公式计算失败: " + expr, e);
                level = (int) (totalExp / 100);
            }
            // 旧版公式兜底：岛屿上有方块时至少为 1 级。
            // 注意：此兜底仅对旧版 level-formula 生效，不得应用到 level-cost 幂函数模型——
            // 在扣除模板基线后，level 0 是合法的初始状态（经验未达到 base 阈值），
            // 若在此处强制提升到 1 级，会用 < base 的经验绕过配置的升 1 级阈值。
            if (level == 0 && results.getBlocksCounted() > 0) level = 1;
        }

        if (level < 0) level = 0;

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

        chunksScanned.addAndGet(tasks.size());

        // 通过 LevelManager 发送进度更新
        int done = results.getTotalChunksScanned() - remainingPositions + chunksScanned.get();
        int total = results.getTotalChunksScanned();
        levelManager.sendProgress(island.getOwnerId(), done, total);
    }

    // ==================== 内部类 ====================

    private record ChunkPos(int x, int z) {}

    private record SnapshotTask(ChunkSnapshot snapshot, int chunkX, int chunkZ, int minY, int maxY) {}
}