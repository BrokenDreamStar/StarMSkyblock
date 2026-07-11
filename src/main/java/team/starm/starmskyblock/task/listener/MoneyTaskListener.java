package team.starm.starmskyblock.task.listener;

import net.milkbowl.vault.economy.Economy;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * EARN_MONEY 任务监听器
 * <p>
 * Vault 余额轮询型监听器。玩家上线时记录初始余额，之后周期性（每 200 tick / 10 秒）
 * 对在线玩家轮询余额，将净增量计入 {@code money} 键。余额减少不计入进度。
 * 依赖 Vault economy，缺失时所有查询静默跳过。
 * </p>
 * <p>
 * 余额查询可能为阻塞 I/O（MySQL/HTTP 后端的 Vault 经济），轮询移至异步线程，避免
 * 每 10s × N 玩家阻塞主线程；仅进度计入（含可能的完成消息 {@code MessageUtil.send}，
 * 需主线程）切回主线程执行。
 * </p>
 */
public class MoneyTaskListener extends BaseTaskListener {

    private final StarMSkyblock plugin;
    /** 玩家 UUID -> 上次记录的余额，用于差值计算。轮询异步、读写跨线程，须并发安全。 */
    private final Map<UUID, Double> lastBalances;
    /** 余额轮询任务是否已调度（仅调度一次） */
    private boolean taskScheduled = false;

    public MoneyTaskListener(TaskManager taskManager) {
        super(taskManager, TaskType.EARN_MONEY);
        this.plugin = StarMSkyblock.getInstance();
        this.lastBalances = new ConcurrentHashMap<>();
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

    /** 首位玩家上线时启动余额轮询定时器，之后去重不再重复调度 */
    private void startBalanceCheckTask() {
        if (taskScheduled) return;
        taskScheduled = true;
        // 异步轮询：getBalance 可能为阻塞 I/O，移出主线程避免每 10s × N 玩家卡顿。
        // 余额读取与差值计算在异步线程，正增量收集后切回主线程计入进度。
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            Economy econ = plugin.getEconomy();
            if (econ == null) return;
            Map<UUID, Integer> toTrack = null;
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    UUID uuid = player.getUniqueId();
                    double current = econ.getBalance(player);
                    Double last = lastBalances.get(uuid);
                    if (last == null) {
                        lastBalances.put(uuid, current);
                        continue;
                    }
                    lastBalances.put(uuid, current);
                    double diff = current - last;
                    if (diff > 0) {
                        if (toTrack == null) toTrack = new HashMap<>();
                        toTrack.put(uuid, (int) Math.round(diff));
                    }
                } catch (Exception ignored) {}
            }
            if (toTrack != null && !toTrack.isEmpty()) {
                Map<UUID, Integer> snapshot = toTrack;
                // 进度计入可能触发完成消息（MessageUtil.send，需主线程），切回主线程处理
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Map.Entry<UUID, Integer> e : snapshot.entrySet()) {
                        Player p = Bukkit.getPlayer(e.getKey());
                        if (p != null) {
                            taskManager.incrementProgress(p, taskType, "money", e.getValue());
                        }
                    }
                });
            }
        }, 200L, 200L);
    }

    /**
     * 同步记录玩家初始余额。仅在 join 时调用一次（单次主线程读取，可接受），
     * 且 join 时即完成写入，早于首次轮询（200 tick 后），避免与异步轮询产生陈旧覆写竞态。
     */
    private void recordBalance(Player player) {
        if (plugin.getEconomy() == null) return;
        try {
            lastBalances.put(player.getUniqueId(), plugin.getEconomy().getBalance(player));
        } catch (Exception e) {
            MessageUtil.consoleWarn("获取玩家 " + player.getName() + " 余额失败");
        }
    }
}
