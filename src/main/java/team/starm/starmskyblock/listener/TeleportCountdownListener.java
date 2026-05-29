package team.starm.starmskyblock.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportCountdownListener implements Listener {

    private static final Map<UUID, CountdownTask> COUNTING_DOWN = new ConcurrentHashMap<>();

    public static void startCountdown(Player player, Location targetLocation, int seconds, String successMessage) {
        UUID uuid = player.getUniqueId();

        CountdownTask existing = COUNTING_DOWN.get(uuid);
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
                    COUNTING_DOWN.remove(uuid);
                    cancel();
                    return;
                }
                ((net.kyori.adventure.audience.Audience) player).sendActionBar(MessageUtil.parse("&a传送倒计时: &e" + remaining + " &a秒..."));
                remaining--;
            }
        };

        countdownTask.task = runnable;
        COUNTING_DOWN.put(uuid, countdownTask);
        runnable.runTaskTimer(StarMSkyblock.getInstance(), 0L, 20L);
    }

    public static void cancelCountdown(Player player) {
        UUID uuid = player.getUniqueId();
        CountdownTask countdownTask = COUNTING_DOWN.remove(uuid);
        if (countdownTask != null) {
            countdownTask.task.cancel();
            ((net.kyori.adventure.audience.Audience) player).sendActionBar(MessageUtil.parse("&c传送已取消！"));
        }
    }

    public static boolean isCountingDown(Player player) {
        return COUNTING_DOWN.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!COUNTING_DOWN.containsKey(player.getUniqueId())) {
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

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        CountdownTask countdownTask = COUNTING_DOWN.remove(uuid);
        if (countdownTask != null) {
            countdownTask.task.cancel();
        }
    }

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
