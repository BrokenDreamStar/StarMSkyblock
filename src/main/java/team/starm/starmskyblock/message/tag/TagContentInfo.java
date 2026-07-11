/*
 * LiteCommandEditor - 原项目: https://github.com/TRCStudioDean/LiteCommandEditor
 * 版权 (c) TRCStudioDean
 * 本文件来源于 LiteCommandEditor 项目，经适配后用于 StarMSkyblock。
 */
package team.starm.starmskyblock.message.tag;

/**
 * 单个颜色标签的提取结果。
 * <p>
 * 记录标签内容、开闭标签原文、在原文中的起止位置及可选属性名，供着色器据此将
 * 标签整体替换为目标颜色串。来源于 LiteCommandEditor 项目。
 */
public class TagContentInfo
{
    private final String content;
    private final String openTag;
    private final String closeTag;
    private final int startPosition;
    private final int endPosition;
    private String attribute = null;

    public TagContentInfo(String content, String openTag, String closeTag, int startPosition, int endPosition) {
        this.content = content;
        this.openTag = openTag;
        this.closeTag = closeTag;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    public String getContent() {
        return content;
    }

    public String getOpenTag() {
        return openTag;
    }

    public String getCloseTag() {
        return closeTag;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public int getEndPosition() {
        return endPosition;
    }

    public String getAttribute() {
        return attribute;
    }

    /**
     * 在原文本中将本标签整体（含属性前缀与开闭标签）替换为指定字符串。
     *
     * @param text        原文本
     * @param replacement 替换串
     * @return 替换后的文本
     */
    public String replace(String text, String replacement) {
        return text.replace((attribute == null ? openTag : openTag.substring(0, openTag.length() - 1) + ":" + attribute + ">") + content + (closeTag == null ? "" : closeTag), replacement);
    }

    /**
     * 从开标签中提取标签名（去除 {@code <}{@code >}，并去掉 {@code :attr} 属性前缀）。
     *
     * @return 标签名
     */
    public String getTagName() {
        return openTag.substring(1, openTag.length() - 1).split(":", -1)[0];
    }

    /**
     * 设置属性名并返回本对象（链式）。属性名区分 {@code <tag:attr>} 与 {@code <tag>} 形式。
     *
     * @param attribute 属性名
     * @return 本对象
     */
    public TagContentInfo setAttribute(String attribute) {
        this.attribute = attribute;
        return this;
    }
}
