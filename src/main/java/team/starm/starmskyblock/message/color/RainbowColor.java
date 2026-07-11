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
 * 彩虹颜色标签处理器。
 * <p>
 * 解析 {@code <rainbow[:saturation:brightness]>text</rainbow>} 标签（属性前缀 {@code !} 表示反向），
 * 按 HSB 色相环为每个字符分配颜色，形成彩虹效果。不支持 RGB 的旧版本回退到最接近的经典颜色。
 * 来源于 LiteCommandEditor 项目。
 */
public class RainbowColor
    implements FunctionalColor
{
    private static final RainbowColor instance = new RainbowColor();

    public static RainbowColor getInstance() {
        return instance;
    }

    /**
     * 解析文本中所有 {@code rainbow} 标签（含无属性简写形式）并着色。
     * <p>
     * 属性形如 {@code [!]offsetX[:offsetY]}，{@code !} 反向；缺失参数使用默认值
     * (offsetX=0, offsetY=20)。逐字符着色后以 {@code <previousColor>} 衔接既有颜色。
     *
     * @param content 待着色文本
     * @return 彩虹着色后的文本
     */
    @Override
    public String coloring(String content) {
        String original = content;
        List<TagContentInfo> colorTags = TagContentExtractor.getTagContentsInfo(content, "rainbow");
        colorTags.addAll(TagContentExtractor.getTagContentsInfo(content, "rainbow", true, false));
        for (TagContentInfo tagContent : colorTags) {
            boolean reverse;
            String text = tagContent.getContent();
            String offsetX;
            String offsetY;
            if (tagContent.getAttribute() != null) {
                reverse = tagContent.getAttribute().substring(0, 1).equals("!");
                String[] parameters = (reverse ? tagContent.getAttribute().substring(1) : tagContent.getAttribute()).split(":", 2);
                if (parameters.length == 2) {
                    offsetX = parameters[0].isEmpty() ? null : parameters[0];
                    offsetY = parameters[1];
                } else {
                    offsetX = parameters[0].isEmpty() ? null : parameters[0];
                    offsetY = null;
                }
            } else {
                reverse = false;
                offsetX = null;
                offsetY = null;
            }
            String[] colors = makeRainbow(text.length(), offsetX != null ? Float.valueOf(offsetX) : 0F, offsetY != null ? Float.valueOf(offsetY) : 20F, reverse);
            content = tagContent.replace(content, (tagContent.getCloseTag() != null ? "<previousColor>" : "") + ColorUtils.coloring(text, colors, ColorUtils.getPreviousTypeface(original, tagContent.getStartPosition())) + (tagContent.getCloseTag() != null ? "</previousColor>" : ""));
        }
        return content;
    }

    /**
     * 沿 HSB 色相环生成 depth 个字符的彩虹颜色数组。
     *
     * @param depth   目标字符数
     * @param offsetX 色相偏移基准（与 offsetY 共同决定步进方向）
     * @param offsetY 步进分母；为 0 时与 offsetX 同时置 1 避免除零
     * @param reverse 是否反向彩虹
     * @return 逐字符的 §-字符串数组
     */
    public String[] makeRainbow(int depth, float offsetX, float offsetY, boolean reverse) {
        String[] colors = new String[depth];
        if (offsetY == 0) offsetX = offsetY = 1; //Prevent division from being zero
        float offset = reverse ? -1 * offsetX / offsetY : offsetX / offsetY;
        for (int i = 0; i < depth; i++) {
            if (ColorUtils.isSupportsRGBVersions()) {
                colors[i] = LegacyColor.toLegacy(Color.getHSBColor(1F / depth * i + offset, 1F, 1F));
            } else {
                colors[i] = LegacyColor.legacyChar(ColorUtils.toNearestColor(Color.getHSBColor(1F / depth * i + offset, 1F, 1F)));
            }
        }
        return colors;
    }
}
