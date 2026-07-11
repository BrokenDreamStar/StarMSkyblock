/*
 * StarMSkyblock - 颜色引擎 Adventure 桥接器。
 * 替代已弃用的 net.md_5.bungee.api.ChatColor。
 */
package team.starm.starmskyblock.message.color;

import java.awt.Color;
import net.kyori.adventure.text.format.TextColor;

/**
 * Adventure 颜色与 legacy §-字符串之间的桥接器，替代已弃用的 {@code net.md_5.bungee.api.ChatColor}。
 * <p>
 * 颜色引擎最终产出 §-字符串（由 {@code MessageUtil.SECTION_SERIALIZER} 反序列化为 Adventure Component），
 * 而 Adventure 原生不提供「bare 颜色 -> §-字符串」的 API（颜色须附着于 Component），故本类以
 * {@link TextColor} 为颜色模型并手动发射 §-字符串，填补该缺口。
 * <p>
 * 行为与 bungee ChatColor 等价：hex 发射统一小写（等价 {@code ChatColor.of(Color).toString()}）。
 * 唯一外观差异：用户直接书写的 {@code &#RRGGBB} 大写 hex 会被规范化为小写，渲染/反序列化/剥色长度均不可观测。
 */
public final class LegacyColor {

    /** translateAmp 识别的全部代码字符（与 bungee ALL_CODES 一致）。 */
    private static final String ALL_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    private LegacyColor() {
    }

    /**
     * 等价 {@code ChatColor.translateAlternateColorCodes('&', text)}：
     * 将 {@code &}+代码 转为 {@code §}+小写代码；{@code &#} 不转换（{@code #} 非代码字符）。
     *
     * @param text 待转换文本
     * @return {@code &} 代码转为 {@code §} 后的文本
     */
    public static String translateAmp(String text) {
        char[] b = text.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == '&' && ALL_CODES.indexOf(b[i + 1]) > -1) {
                b[i] = '§';
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }
        return new String(b);
    }

    /**
     * {@link TextColor} -> legacy §-字符串 {@code §x§r§r§g§g§b§b}（小写），等价 {@code ChatColor.of(Color).toString()}。
     *
     * @param color Adventure 文本颜色
     * @return 对应的 hex §-字符串
     */
    public static String toLegacy(TextColor color) {
        int v = color.value();
        return "§x"
                + nib((v >> 20) & 0xF) + nib((v >> 16) & 0xF)
                + nib((v >> 12) & 0xF) + nib((v >> 8) & 0xF)
                + nib((v >> 4) & 0xF) + nib(v & 0xF);
    }

    /**
     * {@link Color} -> §-字符串（经 {@link TextColor}，小写），等价 {@code ChatColor.of(Color).toString()}。
     *
     * @param color java.awt 颜色
     * @return 对应的 hex §-字符串
     */
    public static String toLegacy(Color color) {
        return toLegacy(TextColor.color(color.getRGB() & 0xFFFFFF));
    }

    /**
     * {@code "#RRGGBB"} -> §-字符串，等价 {@code ChatColor.of(String).toString()}。
     * 对畸形输入（长度非 7 或无 {@code #}）抛 {@link IllegalArgumentException}，与 bungee 一致（被 {@code ColorUtils.toColor} 的 catch 捕获后返回原文）。
     *
     * @param hex 形如 {@code #RRGGBB} 的 hex 字符串
     * @return 对应的 hex §-字符串
     */
    public static String toLegacy(String hex) {
        if (hex.length() != 7 || hex.charAt(0) != '#') {
            throw new IllegalArgumentException("Illegal hex string: " + hex);
        }
        return toLegacy(TextColor.color(Integer.parseInt(hex.substring(1), 16)));
    }

    /**
     * legacy 单字符 -> {@code §c}（覆盖 0-9 a-f r 及 k/l/m/n/o），等价 {@code ChatColor.getByChar(c).toString()}。
     *
     * @param code legacy 颜色/格式代码字符
     * @return {@code §} + code
     */
    public static String legacyChar(char code) {
        return "§" + code;
    }

    /** 单个 hex 半字节 -> {@code §d}（小写）。 */
    private static String nib(int n) {
        return "§" + Character.toLowerCase(Character.forDigit(n, 16));
    }
}
