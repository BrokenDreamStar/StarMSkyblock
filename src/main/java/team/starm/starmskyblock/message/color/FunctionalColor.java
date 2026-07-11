/*
 * LiteCommandEditor - 原项目: https://github.com/TRCStudioDean/LiteCommandEditor
 * 版权 (c) TRCStudioDean
 * 本文件来源于 LiteCommandEditor 项目，经适配后用于 StarMSkyblock。
 */
package team.starm.starmskyblock.message.color;

/**
 * 富文本颜色标签的着色策略接口。
 * <p>
 * 每个实现负责解析一类颜色标签（如 {@code <gradient>}/{@code <rainbow>}），
 * 由 {@link ColorUtils#toColor(String)} 在标签着色阶段统一遍历调用。
 * 来源于 LiteCommandEditor 项目。
 */
public interface FunctionalColor
{
    /**
     * 解析并着色文本中的颜色标签，返回替换后的文本。
     *
     * @param content 待着色文本（已过传统 {@code &} 代码预着色）
     * @return 标签着色后的文本
     */
    String coloring(String content);
}
