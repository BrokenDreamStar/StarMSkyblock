package team.starm.starmskyblock.config;

/**
 * 单个岛屿结构文件的完整配置信息
 * 每个岛屿类型（default、vip、etc.）都对应一个 SchematicConfig 实例，
 * 保存结构文件名 + 主世界/下界/末地 三种环境的传送偏移量。
 */
public class SchematicConfig {

    private final String fileName;
    private final double normalOffsetX;
    private final double normalOffsetY;
    private final double normalOffsetZ;
    private final double netherOffsetX;
    private final double netherOffsetY;
    private final double netherOffsetZ;
    private final double endOffsetX;
    private final double endOffsetY;
    private final double endOffsetZ;

    public SchematicConfig(String fileName,
                           double normalOffsetX, double normalOffsetY, double normalOffsetZ,
                           double netherOffsetX, double netherOffsetY, double netherOffsetZ,
                           double endOffsetX, double endOffsetY, double endOffsetZ) {
        this.fileName = fileName;
        this.normalOffsetX = normalOffsetX;
        this.normalOffsetY = normalOffsetY;
        this.normalOffsetZ = normalOffsetZ;
        this.netherOffsetX = netherOffsetX;
        this.netherOffsetY = netherOffsetY;
        this.netherOffsetZ = netherOffsetZ;
        this.endOffsetX = endOffsetX;
        this.endOffsetY = endOffsetY;
        this.endOffsetZ = endOffsetZ;
    }

    public String getFileName() {
        return fileName;
    }

    public double getNormalOffsetX() { return normalOffsetX; }
    public double getNormalOffsetY() { return normalOffsetY; }
    public double getNormalOffsetZ() { return normalOffsetZ; }

    public double getNetherOffsetX() { return netherOffsetX; }
    public double getNetherOffsetY() { return netherOffsetY; }
    public double getNetherOffsetZ() { return netherOffsetZ; }

    public double getEndOffsetX() { return endOffsetX; }
    public double getEndOffsetY() { return endOffsetY; }
    public double getEndOffsetZ() { return endOffsetZ; }
}
