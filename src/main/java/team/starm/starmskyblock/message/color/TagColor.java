/*
 * LiteCommandEditor - 原项目: https://github.com/TRCStudioDean/LiteCommandEditor
 * 版权 (c) TRCStudioDean
 * 本文件来源于 LiteCommandEditor 项目，经适配后用于 StarMSkyblock。
 */
package team.starm.starmskyblock.message.color;

import java.util.List;
import java.util.Map;

import net.md_5.bungee.api.ChatColor;

import team.starm.starmskyblock.message.tag.TagContentExtractor;
import team.starm.starmskyblock.message.tag.TagContentInfo;

public class TagColor
    implements FunctionalColor
{
    private static final TagColor instance = new TagColor();

    public static TagColor getInstance() {
        return instance;
    }

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
                content = tagContent.replace(content, (tagContent.getCloseTag() != null ? "<previousColor>" : "") + (ColorUtils.isSupportsRGBVersions() ? ChatColor.of(color).toString() : ChatColor.getByChar(ColorUtils.toNearestColor(color))).toString() + text + (tagContent.getCloseTag() != null ? "</previousColor>" : ""));
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
