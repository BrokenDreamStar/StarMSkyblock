package team.starm.starmskyblock.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.bukkit.ChatColor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import team.starm.starmskyblock.message.color.ColorUtils;

/**
 * 基于 Adventure API 的通用消息/颜色工具类。
 * 统一处理含 & 颜色代码的字符串解析、发送、广播和日志打印。
 * 支持 Hex 颜色（&#RRGGBB）和传统 § 格式。
 * 内置静默模式：带有 -s 标记命令的玩家不会收到反馈消息。
 * <p>
 * 颜色管线委托给 ColorUtils（来自 LiteCommandEditor），支持：
 * - & 传统颜色代码
 * - <gradient:color1:color2>text</gradient> 渐变
 * - <rainbow>text</rainbow> 彩虹
 * - <color:name>text</color> / <name>text</name> 单色
 * - <transition:colors:ratio>text</transition> 过渡色
 */
public class MessageUtil {

    private static final Set<UUID> SILENT_PLAYERS = ConcurrentHashMap.newKeySet();

    private static final String LOG_PREFIX = "&7[<gradient:#14bcfe:#495aff>&lStarM Skyblock</gradient>&7]&r";

    // 用于将 ColorUtils.toColor() 输出的 § 字符串解析为 Adventure Component
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    /**
     * 将包含颜色代码的字符串解析为 Component
     */
    public static @NotNull Component parse(@Nullable String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return SECTION_SERIALIZER.deserialize(ColorUtils.toColor(text));
    }

    /**
     * 转换字符串并返回旧版的 § 字符串（用于兼容某些仅支持 String 的旧 API）
     */
    public static String colorize(@Nullable String text) {
        if (text == null) return null;
        return LegacyComponentSerializer.legacySection().serialize(parse(text));
    }

    /**
     * 发送彩色消息给发送者（静默模式下自动跳过玩家消息）
     */
    public static void sendMessage(@NotNull CommandSender sender, @Nullable String message) {
        if (message == null) return;
        if (sender instanceof Player player && SILENT_PLAYERS.contains(player.getUniqueId())) return;
        sender.sendMessage(colorize(message));
    }

    public static void setSilent(@NotNull UUID playerUuid, boolean silent) {
        if (silent) {
            SILENT_PLAYERS.add(playerUuid);
        } else {
            SILENT_PLAYERS.remove(playerUuid);
        }
    }

    public static boolean isSilent(@NotNull UUID playerUuid) {
        return SILENT_PLAYERS.contains(playerUuid);
    }

    /**
     * 广播彩色消息
     */
    public static void broadcast(@Nullable String message) {
        if (message == null) return;
        Bukkit.broadcastMessage(colorize(message));
    }

    /**
     * 向控制台打印彩色日志
     */
    public static void consolePrint(@Nullable String text) {
        if (text == null) return;
        Bukkit.getConsoleSender().sendMessage(colorize(LOG_PREFIX + "&a" + text));
    }

    /**
     * 剔除字符串中的所有颜色代码（包括 & 格式和 § 格式）
     *
     * @param text 原始字符串（可包含 &a, &b, &#RRGGBB 等颜色代码）
     * @return 剔除颜色代码后的纯文本
     */
    public static String stripColor(@Nullable String text) {
        if (text == null) return null;
        return ChatColor.stripColor(colorize(text));
    }

    /**
     * 向控制台打印彩色错误日志
     */
    public static void consoleError(@Nullable String text) {
        if (text == null) return;
        Bukkit.getConsoleSender().sendMessage(colorize(LOG_PREFIX + "&c[ERROR] " + text));
    }

    /**
     * 向控制台打印彩色警告日志
     */
    public static void consoleWarn(@Nullable String text) {
        if (text == null) return;
        Bukkit.getConsoleSender().sendMessage(colorize(LOG_PREFIX + "&e[WARN] " + text));
    }
}
