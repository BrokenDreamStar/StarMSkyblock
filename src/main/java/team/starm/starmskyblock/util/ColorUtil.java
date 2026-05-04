package team.starm.starmskyblock.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 基于 Adventure API 的高级颜色工具类 (修复版)
 */
public class ColorUtil {

    // 支持传统的 & 颜色代码和 Hex 颜色
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    /**
     * 将包含 & 颜色代码的字符串解析为 Component
     * 修复逻辑：直接反序列化为 Component，确保 Adventure 能自动处理 ANSI (控制台) 和颜色码 (游戏内)
     */
    public static @NotNull Component parse(@Nullable String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        // 直接解析 & 代码。不要再进行二次序列化，否则会导致渲染失效
        return LEGACY_SERIALIZER.deserialize(text);
    }

    /**
     * 转换字符串并返回旧版的 § 字符串（用于兼容某些仅支持 String 的旧 API）
     */
    public static String colorize(@Nullable String text) {
        if (text == null) return null;
        return LegacyComponentSerializer.legacySection().serialize(parse(text));
    }

    /**
     * 发送彩色消息给发送者
     */
    public static void sendMessage(@NotNull CommandSender sender, @Nullable String message) {
        if (message == null) return;
        sender.sendMessage(parse(message));
    }

    /**
     * 广播彩色消息
     */
    public static void broadcast(@Nullable String message) {
        if (message == null) return;
        Bukkit.broadcast(parse(message));
    }

    /**
     * 向控制台打印彩色日志
     * 使用 Bukkit.getConsoleSender().sendMessage 确保 Adventure 自动渲染 ANSI 颜色
     */
    public static void consolePrint(@Nullable String text) {
        if (text == null) return;
        Bukkit.getConsoleSender().sendMessage(parse(text));
    }

    /**
     * 向控制台打印彩色错误日志
     */
    public static void consoleError(@Nullable String text) {
        if (text == null) return;
        // 使用 &c 前缀确保红色显示
        Bukkit.getConsoleSender().sendMessage(parse("&c[ERROR] " + text));
    }
}
