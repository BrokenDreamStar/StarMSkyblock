package team.starm.starmskyblock.message;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import team.starm.starmskyblock.util.ColorUtil;

/**
 * 消息工具类，封装 ColorUtil 提供统一的消息发送接口
 */
public class MessageUtil {

    /**
     * 发送彩色消息给发送者
     */
    public static void sendMessage(@NotNull CommandSender sender, @Nullable String message) {
        ColorUtil.sendMessage(sender, message);
    }

    /**
     * 广播彩色消息
     */
    public static void broadcast(@Nullable String message) {
        ColorUtil.broadcast(message);
    }

    /**
     * 向控制台打印彩色日志
     */
    public static void consolePrint(@Nullable String text) {
        ColorUtil.consolePrint(text);
    }
    
    /**
     * 向控制台打印彩色错误日志
     */
    public static void consoleError(@Nullable String text) {
        ColorUtil.consoleError(text);
    }

    /**
     * 将包含 & 颜色代码的字符串解析为 Component
     */
    public static @NotNull Component parse(@Nullable String text) {
        return ColorUtil.parse(text);
    }

    /**
     * 转换字符串并返回旧版的 § 字符串（用于兼容某些仅支持 String 的旧 API）
     */
    public static String colorize(@Nullable String text) {
        return ColorUtil.colorize(text);
    }
}