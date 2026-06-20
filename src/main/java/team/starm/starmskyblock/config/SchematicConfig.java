package team.starm.starmskyblock.config;

/**
 * 单个岛屿结构文件的完整配置信息。
 * 每个岛屿类型（default、vip、etc.）都对应一个 SchematicConfig 实例，
 * 保存结构文件名 + 主世界/下界/末地 三种环境的传送偏移量及朝向。
 * 传送偏移量用于修正玩家传送到岛屿后的精确位置。
 *
 * @param fileName      结构文件名（如 default.schem）
 * @param normalOffsetX 主世界传送偏移
 * @param netherOffsetX 下界传送偏移
 * @param endOffsetX    末地传送偏移
 */
public record SchematicConfig(String fileName,
                              double normalOffsetX, double normalOffsetY, double normalOffsetZ,
                              float normalYaw, float normalPitch,
                              double netherOffsetX, double netherOffsetY, double netherOffsetZ,
                              float netherYaw, float netherPitch,
                              double endOffsetX, double endOffsetY, double endOffsetZ,
                              float endYaw, float endPitch) {

}
