/*
 * LiteCommandEditor - 原项目: https://github.com/TRCStudioDean/LiteCommandEditor
 * 版权 (c) TRCStudioDean
 * 本文件来源于 LiteCommandEditor 项目，经适配后用于 StarMSkyblock。
 */
package team.starm.starmskyblock.message.tag;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TagContentExtractor
{
    /**
     * Extract content info from specified tags.
     * @param text Text
     * @param tagName The tag name
     * @return All the content info that contains specific tag.
     */
    public static List<TagContentInfo> getTagContentsInfo(String text, String tagName) {
        return getTagContentsInfo(text, tagName, true, true);
    }

    /**
     * Extract content info from specified tags.
     * @param text Text
     * @param tagName The tag name
     * @param ignoreCase Whether ignore case
     * @return All the content info that contains specific tag.
     */
    public static List<TagContentInfo> getTagContentsInfo(String text, String tagName, boolean ignoreCase) {
        String openTag = ignoreCase ? "<" + tagName.toLowerCase() + ">" : "<" + tagName + ">";
        String closeTag = ignoreCase ? "</" + tagName.toLowerCase() + ">" : "</" + tagName + ">";
        return getTagContentsInfo(text, openTag, closeTag, ignoreCase);
    }

    /**
     * Extract content info from specified tags.
     * @param text Text
     * @param openTag Open tag
     * @param closeTag Close tag
     * @return All the content info that contains specific tag.
     */
    public static List<TagContentInfo> getTagContentsInfo(String text, String openTag, String closeTag) {
        return getTagContentsInfo(text, openTag, closeTag, false);
    }

    /**
     * Extract content info from specified tags.
     * @param text Text
     * @param openTag Open tag
     * @param closeTag Close tag
     * @param ignoreCase Whether ignore case
     * @return All the content info that contains specific tag.
     */
    public static List<TagContentInfo> getTagContentsInfo(String text, String openTag, String closeTag, boolean ignoreCase) {
        return getTagContentsInfo(text, openTag, closeTag, ignoreCase, new ArrayList<>());
    }

    /**
     * Extract content info from specified tags.
     * @param text Text
     * @param openTag Open tag
     * @param closeTag Close tag
     * @param ignoreCase Whether ignore case
     * @param attributes Tag attributes
     * @return All the content info that contains specific tag.
     */
    public static List<TagContentInfo> getTagContentsInfo(String text, String openTag, String closeTag, boolean ignoreCase, List<String> attributes) {
        List<TagContentInfo> results = new ArrayList<>();
        if (text == null || text.isEmpty()) return results;
        int openTagLength = openTag.length();
        int closeTagLength = closeTag.length();
        int textLength = text.length();
        // 一次性小写化,避免 while 每轮重复全串拷贝
        String textLower = ignoreCase ? text.toLowerCase() : null;
        String openTagLower = ignoreCase ? openTag.toLowerCase() : null;
        int index = 0;
        List<int[]> skip = new ArrayList<>();
        while (index < textLength) {
            String attributeResult = null;
            int extraLength = 0;
            int startPos = -1;
            int tempPos;
            //Search attributes' tag
            if (!attributes.isEmpty()) {
                for (String attribute : attributes) {
                    String newOpenTag = openTag.substring(0, openTagLength - 1) + ":" + attribute + ">";
                    String newOpenTagLower = ignoreCase ? newOpenTag.toLowerCase() : null;
                    tempPos = (ignoreCase ? textLower : text).indexOf(ignoreCase ? newOpenTagLower : newOpenTag, index);
                    if (tempPos != -1 && (startPos > tempPos || startPos == -1)) {
                        startPos = tempPos;
                        extraLength = (":" + attribute).length();
                        attributeResult = attribute;
                    }
                }
            }
            //Search original tag
            tempPos = (ignoreCase ? textLower : text).indexOf(ignoreCase ? openTagLower : openTag, index);
            if (tempPos != -1 && (startPos > tempPos || startPos == -1)){
                startPos = tempPos;
                extraLength = 0;
                attributeResult = null;
            }
            if (startPos == -1) break;
            int contentStart = startPos + openTagLength + extraLength;
            int depth = 1;
            int endPos = -1;
            index = contentStart;
            //Check nesting and find the correct closed tag
            while (index < textLength) {
                //Skip processed inner tags
                for (int[] pos : skip) {
                    if (index >= pos[0] && index <= pos[1]) {
                        index += pos[1] - pos[0];
                        break;
                    }
                }
                if (index + openTagLength <= textLength &&
                    text.regionMatches(ignoreCase, index, openTag, 0, openTagLength)) {
                    depth++;
                } else if (index + closeTagLength <= textLength &&
                    text.regionMatches(ignoreCase, index, closeTag, 0, closeTagLength)) {
                    depth--;
                } else if (!attributes.isEmpty()) {
                    for (String attribute : attributes) {
                        String newOpenTag = openTag.substring(0, openTagLength - 1) + ":" + attribute + ">";
                        int newOpenTagLength = newOpenTag.length();
                        if (index + newOpenTagLength <= textLength &&
                            text.regionMatches(ignoreCase, index, newOpenTag, 0, newOpenTagLength)) {
                            depth++;
                            break;
                        }
                    }
                }
                if (depth == 0) {
                    endPos = index;
                    break;
                }
                index++;
            }
            //Complete tag
            if (endPos != -1) {
                results.add(new TagContentInfo(
                    text.substring(contentStart, endPos),
                    openTag,
                    closeTag,
                    startPos,
                    endPos + closeTagLength
                ).setAttribute(attributeResult));
                skip.add(new int[] {endPos, endPos + closeTagLength});
            //Single tag
            } else {
                results.add(new TagContentInfo(
                    text.substring(contentStart),
                    openTag,
                    null,
                    startPos,
                    text.length()
                ).setAttribute(attributeResult));
            }
            index = contentStart;
        }
        return results;
    }

    /**
     * Get a single tag
     * @param text Text
     * @param openTag Open tag
     * @param closeTag Close tag
     * @param ignoreCase Whether ignore case
     * @return
     */
    public static TagContentInfo getTagContentInfo(String text, String openTag, String closeTag, boolean ignoreCase) {
        if (text == null || text.isEmpty()) return null;
        int openTagLength = openTag.length();
        int closeTagLength = closeTag.length();
        int textLength = text.length();
        String textLower = ignoreCase ? text.toLowerCase() : null;
        String openTagLower = ignoreCase ? openTag.toLowerCase() : null;
        int startPos = (ignoreCase ? textLower : text).indexOf(ignoreCase ? openTagLower : openTag);
        if (startPos == -1) return null;
        int contentStart = startPos + openTagLength;
        int depth = 1;
        int endPos = -1;
        int index = contentStart;
        while (index < textLength) {
            if (index + openTagLength <= textLength &&
                text.regionMatches(ignoreCase, index, openTag, 0, openTagLength)) {
                depth++;
            } else if (index + closeTagLength <= textLength &&
                text.regionMatches(ignoreCase, index, closeTag, 0, closeTagLength)) {
                depth--;
            }
            if (depth == 0) {
                endPos = index;
                break;
            }
            index++;
        }
        //Complete tag
        if (endPos != -1) {
            return new TagContentInfo(
                text.substring(contentStart, endPos),
                openTag,
                closeTag,
                startPos,
                endPos + closeTagLength
            );
        //Single tag
        } else {
            return new TagContentInfo(
                text.substring(contentStart),
                openTag,
                null,
                startPos,
                text.length()
            );
        }
    }

    /**
     * Extract content info from specified tags.
     * @param text Text
     * @param tagName The tag name
     * @param ignoreCase Whether ignore case
     * @param attributes Whether to get attributes
     * @return All the content info that contains specific tag.
     */
    public static List<TagContentInfo> getTagContentsInfo(String text, String tagName, boolean ignoreCase, boolean attributes) {
        List<TagContentInfo> results = new ArrayList<>();
        if (text == null || tagName == null || text.isEmpty() || tagName.isEmpty()) return results;
        if (!attributes) return getTagContentsInfo(text, tagName, ignoreCase);
        String tag = ignoreCase ? tagName.toLowerCase() + ":" : tagName + ":";
        List<String> tagAttributes = getTagAttributes("<" + tag, ">", text, ignoreCase);
        String openTag = ignoreCase ? "<" + tagName.toLowerCase() + ">" : "<" + tagName + ">";
        String closeTag = ignoreCase ? "</" + tagName.toLowerCase() + ">" : "</" + tagName + ">";
        return getTagContentsInfo(text, openTag, closeTag, ignoreCase, tagAttributes);
    }

    /**
     * Get the specified section from the text
     * @param text Text
     * @param targetChar Target character
     * @param ignoreCase Whether ignore case
     * @return
     */
    public static TagContentInfo getSection(String text, char targetChar, boolean ignoreCase) {
        if (text == null || text.isEmpty()) return null;
        int textLength = text.length();
        char effectiveChar = ignoreCase ? Character.toLowerCase(targetChar) : targetChar;
        String textLower = ignoreCase ? text.toLowerCase() : text;
        int index = 0;
        while (index < textLength) {
            int startPos = textLower.indexOf(effectiveChar, index);
            if (startPos == -1) break;
            if (text.charAt(startPos - 1) == '\\') {
                index = startPos + 1;
                continue;
            }
            int contentStart = startPos + 1;
            int offset = 0;
            int endPos = textLower.indexOf(effectiveChar, contentStart);
            while (endPos != -1 && text.charAt(endPos - 1) == '\\') {
                offset++;
                endPos = textLower.indexOf(effectiveChar, contentStart + offset);
            }
            if (endPos != -1) {
                return new TagContentInfo(
                    text.substring(contentStart, endPos).replace("\\" + targetChar, String.valueOf(targetChar)),
                    String.valueOf(targetChar),
                    String.valueOf(targetChar),
                    startPos,
                    endPos
                );
            } else {
                break;
            }
        }
        return null;
    }

    /**
     * Get the specified section from the text
     * @param text Text
     * @param targetChar Target character
     * @param ignoreCase Whether ignore case
     * @return
     */
    public static List<TagContentInfo> getSections(String text, char targetChar, boolean ignoreCase) {
        List<TagContentInfo> results = new ArrayList<>();
        if (text == null || text.isEmpty()) return results;
        int textLength = text.length();
        char effectiveChar = ignoreCase ? Character.toLowerCase(targetChar) : targetChar;
        String textLower = ignoreCase ? text.toLowerCase() : text;
        int index = 0;
        while (index < textLength) {
            int startPos = textLower.indexOf(effectiveChar, index);
            if (startPos == -1) break;
            if (text.charAt(startPos - 1) == '\\') {
                index = startPos + 1;
                continue;
            }
            int contentStart = startPos + 1;
            int offset = 0;
            int endPos = textLower.indexOf(effectiveChar, contentStart);
            while (endPos != -1 && text.charAt(endPos - 1) == '\\') {
                offset++;
                endPos = textLower.indexOf(effectiveChar, contentStart + offset);
            }
            if (endPos != -1) {
                results.add(new TagContentInfo(
                    text.substring(contentStart, endPos).replace("\\" + targetChar, String.valueOf(targetChar)),
                    String.valueOf(targetChar),
                    String.valueOf(targetChar),
                    startPos,
                    endPos
                ));
                index = endPos + 1;
            } else {
                break;
            }
        }
        return results;
    }

    public static List<TagContentInfo> sortTags(List<TagContentInfo> tags) {
        return tags.stream().sorted((TagContentInfo tag1, TagContentInfo tag2) -> tag1.getStartPosition() > tag2.getStartPosition() ? 1 : -1).collect(Collectors.toList());
    }

    private static List<String> getTagAttributes(String startString, String endString, String text, boolean ignoreCase) {
        List<String> result = new ArrayList<>();
        StringBuilder builder = new StringBuilder(text);
        int i = 0;
        int startLength = startString.length();
        int endLength = endString.length();
        // 一次性计算,避免内层每次循环都equalsIgnoreCase
        boolean startEqualsEnd = ignoreCase ? startString.equalsIgnoreCase(endString) : startString.equals(endString);
        while (i <= builder.length() - (startLength <= endLength ? startLength : endLength)) {
            if (i + startLength <= builder.length() && (ignoreCase ? builder.substring(i, i + startLength).equalsIgnoreCase(startString) : builder.substring(i, i + startLength).equals(startString))) {
                int start = i;
                int depth = 1;
                i += startLength;
                while (i <= builder.length() - Math.min(startLength, endLength) && depth > 0) {
                    if (i + startLength <= builder.length() && (ignoreCase ? builder.substring(i, i + startLength).equalsIgnoreCase(startString) : builder.substring(i, i + startLength).equals(startString))) {
                        if (!startEqualsEnd) {
                            depth++;
                            i += startLength;
                        } else {
                            depth = (depth == 1) ? 0 : 1;
                            i += startLength;
                        }
                    } else if (i + endLength <= builder.length() &&
                        !startEqualsEnd &&
                        (ignoreCase ? builder.substring(i, i + endLength).equalsIgnoreCase(endString) : builder.substring(i, i + endLength).equals(endString))) {
                        depth--;
                        i += endLength;
                    } else {
                        i++;
                    }
                }
                if (depth == 0) {
                    String attribute = builder.substring(start + startLength, i - endLength);
                    result.add(attribute);
                    builder.delete(i - endLength, i);
                    builder.delete(start, start + startLength);
                    i = start;
                }
            } else {
                i++;
            }
        }
        return result;
    }
}
