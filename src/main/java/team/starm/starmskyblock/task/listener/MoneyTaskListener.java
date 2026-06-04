package team.starm.starmskyblock.task.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MoneyTaskListener extends BaseTaskListener {

    private final StarMSkyblock plugin;
    private final Map<UUID, Double> lastBalances;
    private boolean taskScheduled = false;

    public MoneyTaskListener(TaskManager taskManager) {
        super(taskManager, TaskType.EARN_MONEY);
        this.plugin = StarMSkyblock.getInstance();
        this.lastBalances = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        recordBalance(player);
        startBalanceCheckTask();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastBalances.remove(event.getPlayer().getUniqueId());
    }

    private void startBalanceCheckTask() {
        if (taskScheduled) return;
        taskScheduled = true;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.getEconomy() == null) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                checkAndTrack(player);
            }
        }, 200L, 200L);
    }

    private void recordBalance(Player player) {
        if (plugin.getEconomy() == null) return;
        try {
            lastBalances.put(player.getUniqueId(), plugin.getEconomy().getBalance(player));
        } catch (Exception e) {
            MessageUtil.consoleWarn("获取玩家 " + player.getName() + " 余额失败");
        }
    }

    private void checkAndTrack(Player player) {
        try {
            UUID uuid = player.getUniqueId();
            double current = plugin.getEconomy().getBalance(player);
            Double last = lastBalances.get(uuid);
            if (last == null) {
                lastBalances.put(uuid, current);
                return;
            }
            double diff = current - last;
            lastBalances.put(uuid, current);
            if (diff > 0) {
                taskManager.incrementProgress(player, taskType, "money", (int) Math.round(diff));
            }
        } catch (Exception ignored) {}
    }
}
