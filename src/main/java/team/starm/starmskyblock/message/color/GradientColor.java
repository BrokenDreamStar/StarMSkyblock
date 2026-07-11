/*
 * LiteCommandEditor - 原项目: https://github.com/TRCStudioDean/LiteCommandEditor
 * 版权 (c) TRCStudioDean
 * 本文件来源于 LiteCommandEditor 项目，经适配后用于 StarMSkyblock。
 */
package team.starm.starmskyblock.message.color;

import java.awt.Color;
import java.util.List;

import team.starm.starmskyblock.message.tag.TagContentExtractor;
import team.starm.starmskyblock.message.tag.TagContentInfo;

/**
 * 渐变颜色标签处理器。
 * <p>
 * 解析 {@code <gradient:color1:color2[:...]>text</gradient>} 标签，按文本长度在多个
 * 颜色间线性插值生成逐字符渐变。单色退化、颜色数 >= 字符数、颜色数 < 字符数三种情形分别处理。
 * 来源于 LiteCommandEditor 项目。
 */
public class GradientColor
    implements FunctionalColor
{
    private static final GradientColor instance = new GradientColor();

    public static GradientColor getInstance() {
        return instance;
    }

    /**
     * 解析文本中所有 {@code gradient} 标签并着色。
     * <p>
     * 对每个标签按其内容长度生成渐变色数组，再用 {@link ColorUtils#coloring} 逐字符着色，
     * 并以 {@code <previousColor>} 占位符衔接标签外的既有颜色/字体。
     *
     * @param content 待着色文本
     * @return 渐变着色后的文本
     */
    @Override
    public String coloring(String content) {
        String original = content;
        List<TagContentInfo> colorTags = TagContentExtractor.getTagContentsInfo(content, "gradient");
        for (TagContentInfo tagContent : colorTags) {
            if (tagContent.getAttribute() == null) continue;
            String[] colorList = tagContent.getAttribute().split(":", -1);
            String text = tagContent.getContent();
            String[] colors = makeGradient(colorList, text.length());
            content = tagContent.replace(content, (tagContent.getCloseTag() != null ? "<previousColor>" : "") + ColorUtils.coloring(text, colors, ColorUtils.getPreviousTypeface(original, tagContent.getStartPosition())) + (tagContent.getCloseTag() != null ? "</previousColor>" : ""));
        }
        return content;
    }

    /**
     * 在指定颜色列表间线性插值，生成 depth 个字符长度的渐变颜色数组。
     * <p>
     * 不支持 RGB 的旧版本服务端会回退到最接近的经典颜色字符。
     *
     * @param containsColors 颜色名/Hex 列表
     * @param depth          目标字符数（渐变采样点数）
     * @return 逐字符的 §-字符串数组
     */
    public static String[] makeGradient(String[] containsColors, int depth) {
        String[] result = new String[depth];
        Color[] colors = new Color[depth];
        if (depth == 0 || containsColors.length == 0) return result;
        if (containsColors.length == 1) {
            Color color = ColorUtils.getColor(containsColors[0]);
            for (int i = 0;i < depth;i++) {
                colors[i] = color;
            }
        } else if (depth <= containsColors.length) {
            for (int i = 0;i < depth;i++) {
                int index = (int) ((containsColors.length - 1) * (i / (double) (depth - 1)));
                colors[i] = ColorUtils.getColor(containsColors[index]);
            }
        } else {
            for (int i = 0;i < depth;i++) {
                float ratio = (float) i / (depth - 1);
                if (ratio <= 0) {
                    colors[i] = ColorUtils.getColor(containsColors[0]);
                } else if (ratio >= 1) {
                    colors[i] = ColorUtils.getColor(containsColors[containsColors.length - 1]);
                } else {
                    float segment = 1.0F / (containsColors.length - 1);
                    int index = (int) (ratio / segment);
                    float localRatio = (ratio - segment * index) / segment;
                    Color c1 = ColorUtils.getColor(containsColors[index]);
                    Color c2 = ColorUtils.getColor(containsColors[index + 1]);
                    colors[i] = new Color(
                        (int) (c1.getRed() + localRatio * (c2.getRed() - c1.getRed())),
                        (int) (c1.getGreen() + localRatio * (c2.getGreen() - c1.getGreen())),
                        (int) (c1.getBlue() + localRatio * (c2.getBlue() - c1.getBlue()))
                    );
                }
            }
        }
        for (int i = 0;i < result.length;i++) {
            if (ColorUtils.isSupportsRGBVersions()) {
                result[i] = LegacyColor.toLegacy(colors[i]);
            } else {
                result[i] = LegacyColor.legacyChar(ColorUtils.toNearestColor(colors[i]));
            }
        }
        return result;
    }
}
