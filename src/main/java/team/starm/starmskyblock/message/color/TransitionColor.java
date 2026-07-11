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
 * 过渡颜色标签处理器。
 * <p>
 * 解析 {@code <transition:color1:color2[:...]:ratio>text</transition>} 标签，按 ratio
 * 在颜色列表间线性插值取单一过渡色（与渐变不同：过渡色整段文本同色，渐变逐字符变色）。
 * ratio 位于最后一项，无法解析为 float 时跳过该标签。来源于 LiteCommandEditor 项目。
 */
public class TransitionColor
    implements FunctionalColor
{
    private static final TransitionColor instance = new TransitionColor();

    public static TransitionColor getInstance() {
        return instance;
    }

    /**
     * 解析文本中所有 {@code transition} 标签并着色。
     * <p>
     * 末位参数为 ratio（0~1），其余为颜色列表；按 ratio 取单一过渡色应用于整段文本，
     * 并以 {@code <previousColor>} 衔接标签外的既有字体。
     *
     * @param content 待着色文本
     * @return 过渡着色后的文本
     */
    @Override
    public String coloring(String content) {
        String original = content;
        List<TagContentInfo> colorTags = TagContentExtractor.getTagContentsInfo(content, "transition");
        for (TagContentInfo tagContent : colorTags) {
            if (tagContent.getAttribute() == null) continue;
            String[] colorList = tagContent.getAttribute().split(":", -1);
            String text = tagContent.getContent();
            if (colorList.length >= 2 && isFloat(colorList[colorList.length - 1])) {
                float ratio = Float.valueOf(colorList[colorList.length - 1]);
                String[] colors = new String[colorList.length - 1];
                for (int i = 0;i < colorList.length - 1;i++) {
                    colors[i] = colorList[i];
                }
                content = tagContent.replace(content, (tagContent.getCloseTag() != null ? "<previousColor>" : "") + makeTransition(colors, ratio) + ColorUtils.getPreviousTypeface(original, tagContent.getStartPosition()) + text + (tagContent.getCloseTag() != null ? "</previousColor>" : ""));
            }
        }
        return content;
    }

    /**
     * 按 ratio 在颜色列表间线性插值，取单一过渡色。
     * <p>
     * 不支持 RGB 的旧版本回退到最接近的经典颜色字符。
     *
     * @param containsColors 颜色名/Hex 列表
     * @param ratio          插值比例（0~1）
     * @return 插值后的 §-字符串
     */
    public static String makeTransition(String[] containsColors, float ratio) {
        if (containsColors.length == 0) return LegacyColor.legacyChar('r');
        Color color;
        if (ratio <= 0 || containsColors.length == 1) {
            color = ColorUtils.getColor(containsColors[0]);
        } else if (ratio >= 1) {
            color = ColorUtils.getColor(containsColors[containsColors.length - 1]);
        } else {
            float segment = 1.0F / (containsColors.length - 1);
            int index = (int) (ratio / segment);
            float localRatio = (ratio - segment * index) / segment;
            Color c1 = ColorUtils.getColor(containsColors[index]);
            Color c2 = ColorUtils.getColor(containsColors[index + 1]);
            color = new Color(
                (int) (c1.getRed() + localRatio * (c2.getRed() - c1.getRed())),
                (int) (c1.getGreen() + localRatio * (c2.getGreen() - c1.getGreen())),
                (int) (c1.getBlue() + localRatio * (c2.getBlue() - c1.getBlue()))
            );
        }
        if (ColorUtils.isSupportsRGBVersions()) {
            return LegacyColor.toLegacy(color);
        } else {
            return LegacyColor.legacyChar(ColorUtils.toNearestColor(color));
        }
    }

    private static boolean isFloat(String value) {
        try {
            Float.valueOf(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
