/*
 * LiteCommandEditor - 原项目: https://github.com/TRCStudioDean/LiteCommandEditor
 * 版权 (c) TRCStudioDean
 * 本文件来源于 LiteCommandEditor 项目，经适配后用于 StarMSkyblock。
 */
package team.starm.starmskyblock.message.tag;

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

    public String replace(String text, String replacement) {
        return text.replace((attribute == null ? openTag : openTag.substring(0, openTag.length() - 1) + ":" + attribute + ">") + content + (closeTag == null ? "" : closeTag), replacement);
    }

    public String getTagName() {
        return openTag.substring(1, openTag.length() - 1).split(":", -1)[0];
    }

    public TagContentInfo setAttribute(String attribute) {
        this.attribute = attribute;
        return this;
    }
}
