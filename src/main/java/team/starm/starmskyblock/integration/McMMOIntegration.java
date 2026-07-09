package team.starm.starmskyblock.integration;

import com.gmail.nossr50.api.ExperienceAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * mcMMO 集成工具类 -- 封装与 mcMMO 插件的所有交互。
 * <p>
 * 提供 mcMMO 可用性检测和岛屿成员 PowerLevel 汇总功能。
 * 所有方法均可安全调用（当 mcMMO 未安装时静默返回默认值）。
 * <p>
 * mcMMO 的 PowerLevel = 所有非子技能等级之和（ACROBATICS, ALCHEMY, ARCHERY, AXES,
 * CROSSBOWS, EXCAVATION, FISHING, HERBALISM, MACES, MINING, REPAIR, SPEARS,
 * SWORDS, TAMING, TRIDENTS, UNARMED, WOODCUTTING）。
 */
public class McMMOIntegration {

    private static final String PLUGIN_NAME = "mcMMO";

    /**
     * 检查服务器是否已安装并启用了 mcMMO 插件。
     */
    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin(PLUGIN_NAME) != null;
    }

    /**
     * 获取岛屿所有成员的 mcMMO PowerLevel 明细和总和。
     * <p>
     * 在线成员优先调用 {@link ExperienceAPI#getPowerLevel(Player)} 取内存中的实时值（含未落盘 XP），
     * 但该方法依赖 {@code UserManager} 中的内存 profile：玩家刚上线 profile 异步加载未完成时会
     * 静默返回 0 或抛 {@code McMMOPlayerNotFoundException}。此时回退到
     * {@link ExperienceAPI#getPowerLevelOffline(UUID)}，它内部调用
     * {@code DatabaseManager.loadPlayerProfile(uuid)} 直接读取 mcMMO 存储，返回已落盘的真实
     * PowerLevel。整个查询在异步线程执行（{@link CompletableFuture#supplyAsync}），磁盘读不阻塞主线程。
     *
     * @param island 目标岛屿
     * @return 包含全体成员 PowerLevel 明细和总和的 {@link AuraSkillsIslandResult}
     */
    public static CompletableFuture<AuraSkillsIslandResult> getIslandResult(Island island) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(new AuraSkillsIslandResult(0, List.of()));
        }

        // 在调用线程（主线程）采集成员 UUID 列表，避免离线查询时跨线程读取岛屿成员集合
        List<UUID> allUuids = new ArrayList<>();
        allUuids.add(island.getOwnerId());
        allUuids.addAll(island.getMembers().keySet());

        if (allUuids.isEmpty()) {
            return CompletableFuture.completedFuture(new AuraSkillsIslandResult(0, List.of()));
        }

        return CompletableFuture.supplyAsync(() -> {
            int total = 0;
            List<MemberSkillData> memberDataList = new ArrayList<>();

            for (UUID uuid : allUuids) {
                Player onlinePlayer = Bukkit.getPlayer(uuid);
                boolean online = onlinePlayer != null && onlinePlayer.isOnline();
                String playerName = online ? onlinePlayer.getName() : getOfflinePlayerName(uuid);

                int pl = queryPowerLevel(uuid, onlinePlayer, online);
                total += pl;
                memberDataList.add(new MemberSkillData(playerName, pl));
            }

            return new AuraSkillsIslandResult(total, memberDataList);
        });
    }

    /**
     * 查询单个成员的 mcMMO PowerLevel，对 mcMMO API 的各种异常与未加载状态保持健壮。
     * <p>
     * 在线玩家优先用 {@link ExperienceAPI#getPowerLevel(Player)} 取实时值；若抛异常
     * （profile 未加载）或返回 0（可能是 profile 未加载的假 0），回退到
     * {@link ExperienceAPI#getPowerLevelOffline(UUID)} 读存储。真实 0 级玩家两边都是 0，回退无副作用。
     */
    private static int queryPowerLevel(UUID uuid, Player onlinePlayer, boolean online) {
        if (online) {
            int onlinePl;
            try {
                onlinePl = ExperienceAPI.getPowerLevel(onlinePlayer);
            } catch (Exception e) {
                // McMMOPlayerNotFoundException 等：profile 未加载进 UserManager，回退存储查询
                return safeOfflinePowerLevel(uuid);
            }
            // 在线 > 0 说明 profile 已加载、数据有效（含未落盘 XP），直接采用；
            // 返回 0 可能是 profile 未加载的假 0，回退存储确认。
            return onlinePl > 0 ? onlinePl : safeOfflinePowerLevel(uuid);
        }
        return safeOfflinePowerLevel(uuid);
    }

    /**
     * 离线查询 PowerLevel 的安全封装，吞掉任何异常（如 InvalidPlayerException）并返回 0。
     */
    private static int safeOfflinePowerLevel(UUID uuid) {
        try {
            return ExperienceAPI.getPowerLevelOffline(uuid);
        } catch (Exception e) {
            MessageUtil.consoleWarn("mcMMO 离线 PowerLevel 查询失败 (uuid=" + uuid + "): "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * 获取离线玩家名（回退方法）。
     */
    private static String getOfflinePlayerName(UUID uuid) {
        try {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name != null) return name;
        } catch (Exception ignored) {
        }
        // 最终回退
        String uuidStr = uuid.toString();
        return uuidStr.substring(0, 8);
    }
}
