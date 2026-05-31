/*
 * LiteCommandEditor - 原项目: https://github.com/TRCStudioDean/LiteCommandEditor
 * 版权 (c) TRCStudioDean
 * 本文件来源于 LiteCommandEditor 项目，经适配后用于 StarMSkyblock。
 */
package team.starm.starmskyblock.message.color;

import java.awt.Color;
import java.util.List;

import net.md_5.bungee.api.ChatColor;

import team.starm.starmskyblock.message.tag.TagContentExtractor;
import team.starm.starmskyblock.message.tag.TagContentInfo;

public class TransitionColor
    implements FunctionalColor
{
    private static final TransitionColor instance = new TransitionColor();

    public static TransitionColor getInstance() {
        return instance;
    }

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

    public static ChatColor makeTransition(String[] containsColors, float ratio) {
        if (containsColors.length == 0) return ChatColor.getByChar('r');
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
            return ChatColor.of(color);
        } else {
            return ChatColor.getByChar(ColorUtils.toNearestColor(color));
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
