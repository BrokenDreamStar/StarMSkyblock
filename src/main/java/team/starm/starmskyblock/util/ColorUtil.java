package team.starm.starmskyblock.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 基于 Adventure API 的高级颜色工具类 (修复版)
 */
public class ColorUtil {

    private static final Set<UUID> SILENT_PLAYERS = new HashSet<>();

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
     * 发送彩色消息给发送者（静默模式下自动跳过玩家消息）
     * 注意：Spigot API 的 CommandSender 没有 sendMessage(Component) 方法，
     * 需转为旧版 § 字符串后发送。
     */
    public static void sendMessage(@NotNull CommandSender sender, @Nullable String message) {
        if (message == null) return;
        if (sender instanceof Player && SILENT_PLAYERS.contains(((Player) sender).getUniqueId())) return;
        sender.sendMessage(colorize(message));
    }

    /**
     * 设置玩家是否进入静默模式（不接收命令反馈消息）
     */
    public static void setSilent(@NotNull UUID playerUuid, boolean silent) {
        if (silent) {
            SILENT_PLAYERS.add(playerUuid);
        } else {
            SILENT_PLAYERS.remove(playerUuid);
        }
    }

    /**
     * 检查玩家是否处于静默模式
     */
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
        Bukkit.getConsoleSender().sendMessage(colorize(text));
    }

    /**
     * 向控制台打印彩色错误日志
     */
    public static void consoleError(@Nullable String text) {
        if (text == null) return;
        Bukkit.getConsoleSender().sendMessage(colorize("&c[ERROR] " + text));
    }
}
