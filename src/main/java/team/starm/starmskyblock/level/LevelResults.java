package team.starm.starmskyblock.level;

import org.bukkit.Material;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 岛屿等级计算的结果数据容器。
 * <p>
 * 保存单次扫描的所有输出：总分、等级、方块计数明细等。
 */
public class LevelResults {

    /** 累计经验值（已扣除超阈值部分） */
    private double totalExperience;
    /** 计算得出的岛屿等级 */
    private int level;
    /** 参与计数的方块总数 */
    private long blocksCounted;
    /** 每种方块的数量统计 */
    private final Map<Material, Long> blockCounts = new HashMap<>();
    /** 各世界扫描的区块数 */
    private int totalChunksScanned;
    /** 实际扫描的世界数 */
    private int worldsScanned;
    /** 扫描耗时（毫秒） */
    private long timeTaken;
    /** 超过阈值的方块数量（方块名 → 被截断的数量） */
    private final Map<Material, Long> blocksOverLimit = new HashMap<>();

    public double getTotalExperience() {
        return totalExperience;
    }

    public void setTotalExperience(double totalExperience) {
        this.totalExperience = totalExperience;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public long getBlocksCounted() {
        return blocksCounted;
    }

    public void setBlocksCounted(long blocksCounted) {
        this.blocksCounted = blocksCounted;
    }

    public Map<Material, Long> getBlockCounts() {
        return Collections.unmodifiableMap(blockCounts);
    }

    public void addBlockCount(Material material, long count) {
        blockCounts.merge(material, count, Long::sum);
    }

    public int getTotalChunksScanned() {
        return totalChunksScanned;
    }

    public void setTotalChunksScanned(int totalChunksScanned) {
        this.totalChunksScanned = totalChunksScanned;
    }

    public int getWorldsScanned() {
        return worldsScanned;
    }

    public void setWorldsScanned(int worldsScanned) {
        this.worldsScanned = worldsScanned;
    }

    public long getTimeTaken() {
        return timeTaken;
    }

    public void setTimeTaken(long timeTaken) {
        this.timeTaken = timeTaken;
    }

    public Map<Material, Long> getBlocksOverLimit() {
        return Collections.unmodifiableMap(blocksOverLimit);
    }

    public void addBlockOverLimit(Material material, long overLimitCount) {
        blocksOverLimit.merge(material, overLimitCount, Long::sum);
    }
}