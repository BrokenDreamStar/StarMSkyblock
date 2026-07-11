package team.starm.starmskyblock.message.color;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link LegacyColor} 的单元测试——全纯逻辑，无 Bukkit 依赖。
 * 金值来源于对 bungeecord-chat ChatColor.of(Color)/of(String)/getByChar/translateAlternateColorCodes
 * 源码的逐字确认（§x + 6×§+digit：of(Color) 小写，of(String) 保留输入大小写）。
 */
class LegacyColorTest {

    // ==================== translateAmp ====================

    @Test
    void translateAmpLowercase() {
        assertEquals("§aHello", LegacyColor.translateAmp("&aHello"));
    }

    @Test
    void translateAmpUppercase() {
        // translateAlternateColorCodes 小写化代码字符
        assertEquals("§aHello", LegacyColor.translateAmp("&AHello"));
    }

    @Test
    void translateAmpHashNotTranslated() {
        // # 不是代码字符，&# 的 & 保留（既有 quirk）
        assertEquals("&#FF0000", LegacyColor.translateAmp("&#FF0000"));
    }

    @Test
    void translateAmpMultiple() {
        assertEquals("§a§b§c", LegacyColor.translateAmp("&a&b&c"));
    }

    @Test
    void translateAmpNoCodes() {
        assertEquals("Hello", LegacyColor.translateAmp("Hello"));
    }

    @Test
    void translateAmpEmpty() {
        assertEquals("", LegacyColor.translateAmp(""));
    }

    // ==================== toLegacy(Color) ====================

    @Test
    void toLegacyColorRed() {
        // bungee ChatColor.of(Color(255,0,0)).toString() → §x§f§f§0§0§0§0
        assertEquals("§x§f§f§0§0§0§0", LegacyColor.toLegacy(new Color(255, 0, 0)));
    }

    @Test
    void toLegacyColorGreen() {
        assertEquals("§x§0§0§f§f§0§0", LegacyColor.toLegacy(new Color(0, 255, 0)));
    }

    @Test
    void toLegacyColorBlue() {
        assertEquals("§x§0§0§0§0§f§f", LegacyColor.toLegacy(new Color(0, 0, 255)));
    }

    @Test
    void toLegacyColorBlack() {
        assertEquals("§x§0§0§0§0§0§0", LegacyColor.toLegacy(new Color(0, 0, 0)));
    }

    @Test
    void toLegacyColorWhite() {
        assertEquals("§x§f§f§f§f§f§f", LegacyColor.toLegacy(new Color(255, 255, 255)));
    }

    // ==================== toLegacy(String) ====================

    @Test
    void toLegacyHexLowercase() {
        // 小写输入 → 小写输出
        assertEquals("§x§f§f§0§0§0§0", LegacyColor.toLegacy("#ff0000"));
    }

    @Test
    void toLegacyHexUppercase() {
        // 大写输入 → 规范化为小写（渲染/反序列化大小写不敏感）
        assertEquals("§x§f§f§0§0§0§0", LegacyColor.toLegacy("#FF0000"));
    }

    @Test
    void toLegacyHexMixed() {
        assertEquals("§x§a§b§c§d§e§f", LegacyColor.toLegacy("#aBcDeF"));
    }

    @Test
    void toLegacyHexInvalidLength() {
        // bungee ChatColor.of(String) 要求 length==7 且始 '#'
        assertThrows(IllegalArgumentException.class, () -> LegacyColor.toLegacy("#abc"));
    }

    @Test
    void toLegacyHexNoHash() {
        assertThrows(IllegalArgumentException.class, () -> LegacyColor.toLegacy("GGGGGG"));
    }

    @Test
    void toLegacyHexNonHex() {
        // Integer.parseInt 抛 NumberFormatException → IllegalArgumentException 被包装
        assertThrows(IllegalArgumentException.class, () -> LegacyColor.toLegacy("#GGGGGG"));
    }

    // ==================== legacyChar ====================

    @Test
    void legacyCharStandard() {
        assertEquals("§c", LegacyColor.legacyChar('c'));
    }

    @Test
    void legacyCharReset() {
        assertEquals("§r", LegacyColor.legacyChar('r'));
    }

    @Test
    void legacyCharBold() {
        assertEquals("§l", LegacyColor.legacyChar('l'));
    }

    @Test
    void legacyCharDigit() {
        assertEquals("§0", LegacyColor.legacyChar('0'));
    }
}
