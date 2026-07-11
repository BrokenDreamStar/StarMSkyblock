package team.starm.starmskyblock.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.audience.Audience;

/**
 * 传送倒计时监听器
 * <p>
 * 为玩家提供带倒计时与移动取消机制的延迟传送：传送开始后逐秒在 action bar 显示剩余秒数，
 * 玩家一旦跨方块移动或退出服务器即取消传送。倒计时任务以 BukkitRunnable 计时器驱动，
 * 按玩家 UUID 维护进行中的倒计时，同一玩家重新开始传送会取消上一个倒计时。
 * </p>
 */
public class TeleportCountdownListener implements Listener {

    /** 插件主类，用于调度 BukkitRunnable 计时任务 */
    private final JavaPlugin plugin;
    /** 进行中的倒计时任务索引：玩家 UUID -> 倒计时任务，保证同一玩家至多一个倒计时 */
    private final Map<UUID, CountdownTask> countingDown = new ConcurrentHashMap<>();

    public TeleportCountdownListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 开始一次传送倒计时。
     * <p>若该玩家已有进行中的倒计时则先取消旧的；逐秒递减并在归零时执行传送、发送成功消息。</p>
     *
     * @param player          目标玩家
     * @param targetLocation  传送目标位置
     * @param seconds         倒计时秒数
     * @param successMessage  传送完成时发送的消息
     */
    public void startCountdown(Player player, Location targetLocation, int seconds, String successMessage) {
        UUID uuid = player.getUniqueId();

        CountdownTask existing = countingDown.get(uuid);
        if (existing != null) {
            existing.task.cancel();
        }

        CountdownTask countdownTask = new CountdownTask(player, targetLocation, seconds, successMessage);

        BukkitRunnable runnable = new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (remaining <= 0) {
                    player.teleport(targetLocation);
                    MessageUtil.sendMessage(player, successMessage);
                    countingDown.remove(uuid);
                    cancel();
                    return;
                }
                ((Audience) player).sendActionBar(
                        MessageUtil.parse(MessageUtil.format("teleport.countdown.actionbar", Map.of("remaining", remaining))));
                remaining--;
            }
        };

        countdownTask.task = runnable;
        countingDown.put(uuid, countdownTask);
        runnable.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * 取消玩家的传送倒计时，停止计时任务并在 action bar 提示已取消。
     *
     * @param player 目标玩家
     */
    public void cancelCountdown(Player player) {
        UUID uuid = player.getUniqueId();
        CountdownTask countdownTask = countingDown.remove(uuid);
        if (countdownTask != null) {
            countdownTask.task.cancel();
            ((Audience) player).sendActionBar(MessageUtil.parse("&c传送已取消！"));
        }
    }

    /** 查询玩家是否正处于传送倒计时中 */
    public boolean isCountingDown(Player player) {
        return countingDown.containsKey(player.getUniqueId());
    }

    /**
     * 监听玩家移动事件
     * <p>
     * 玩家在倒计时期间一旦发生跨方块水平移动（X 或 Z 变化）即取消传送，避免玩家边走边传送。
     * </p>
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!countingDown.containsKey(player.getUniqueId())) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()) {
            cancelCountdown(player);
        }
    }

    /**
     * 监听玩家退出事件
     * <p>玩家退出时清理其倒计时任务，避免计时器在离线玩家上空转。</p>
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        CountdownTask countdownTask = countingDown.remove(uuid);
        if (countdownTask != null) {
            countdownTask.task.cancel();
        }
    }

    /** 单次倒计时的上下文：绑定玩家、目标位置、成功消息，并持有实际驱动的 BukkitRunnable */
    private static class CountdownTask {
        final Player player;
        final Location targetLocation;
        final String successMessage;
        BukkitRunnable task;

        CountdownTask(Player player, Location targetLocation, int seconds, String successMessage) {
            this.player = player;
            this.targetLocation = targetLocation;
            this.successMessage = successMessage;
        }
    }
}
