/*
 * LiteCommandEditor - 原项目: https://github.com/TRCStudioDean/LiteCommandEditor
 * 版权 (c) TRCStudioDean
 * 本文件来源于 LiteCommandEditor 项目，经适配后用于 StarMSkyblock。
 */
package team.starm.starmskyblock.message.color;

import java.util.List;
import java.util.Map;

import team.starm.starmskyblock.message.tag.TagContentExtractor;
import team.starm.starmskyblock.message.tag.TagContentInfo;

/**
 * 单色标签处理器。
 * <p>
 * 支持两种写法：
 * <ul>
 *   <li>{@code <color:name>text</color>} 或 {@code <color:#RRGGBB>text</color>}：以属性指定颜色</li>
 *   <li>{@code <name>text</name>}：颜色/字体名直接作为标签名（如 {@code <red>..</red>}、{@code <bold>..</bold>}）</li>
 * </ul>
 * 来源于 LiteCommandEditor 项目。
 */
public class TagColor
    implements FunctionalColor
{
    private static final TagColor instance = new TagColor();

    public static TagColor getInstance() {
        return instance;
    }

    /**
     * 解析文本中所有单色标签并替换为对应的 {@code §} 颜色/字体代码。
     * <p>
     * 先处理 {@code <color:...>} 形式（颜色名或 Hex），再把每个已知颜色/字体名作为标签名
     * 扫描替换。Hex 颜色在不支持 RGB 的旧版本回退到最接近的经典颜色。
     *
     * @param content 待着色文本
     * @return 单色标签着色后的文本
     */
    @Override
    public String coloring(String content) {
        Map<String, String> colorAndTypefaceNames = ColorUtils.getColorAndTypefaceNames();
        // Tag with color name
        List<TagContentInfo> colorTags = TagContentExtractor.getTagContentsInfo(content, "color");
        for (TagContentInfo tagContent : colorTags) {
            if (tagContent.getAttribute() == null) continue;
            String text = tagContent.getContent();
            String color = tagContent.getAttribute().toLowerCase();
            if (colorAndTypefaceNames.containsKey(color)) { //Example: <color:red>
                content = tagContent.replace(content, (tagContent.getCloseTag() != null ? "<previousColor>" : "") + colorAndTypefaceNames.get(color) + text + (tagContent.getCloseTag() != null ? "</previousColor>" : ""));
            } else if (color.startsWith("#")) { //Example: <color:#FF0000>
                content = tagContent.replace(content, (tagContent.getCloseTag() != null ? "<previousColor>" : "") + (ColorUtils.isSupportsRGBVersions() ? LegacyColor.toLegacy(color) : LegacyColor.legacyChar(ColorUtils.toNearestColor(color))) + text + (tagContent.getCloseTag() != null ? "</previousColor>" : ""));
            }
        }
        // Color name as tag
        for (String symbols : colorAndTypefaceNames.keySet()) {
            List<TagContentInfo> contents = TagContentExtractor.getTagContentsInfo(content, symbols, true, false);
            for (TagContentInfo tagContent : contents) {
                String text = tagContent.getContent();
                String color = tagContent.getTagName();
                content = tagContent.replace(content, (tagContent.getCloseTag() != null ? "<previousColor>" : "") + colorAndTypefaceNames.get(color) + text + (tagContent.getCloseTag() != null ? "</previousColor>" : ""));
            }
        }
        return content;
    }
}
