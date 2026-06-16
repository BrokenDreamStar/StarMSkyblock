package team.starm.starmskyblock.integration;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AuraSkills 集成工具类 —— 封装与 AuraSkills 插件的所有交互。
 * <p>
 * 提供 AuraSkills 可用性检测和岛屿成员 PowerLevel 汇总功能。
 * 所有方法均可安全调用（当 AuraSkills 未安装时静默返回默认值）。
 */
public class AuraSkillsIntegration {

    private static final String PLUGIN_NAME = "AuraSkills";

    /**
     * 检查服务器是否已安装并启用了 AuraSkills 插件。
     */
    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin(PLUGIN_NAME) != null;
    }

    /**
     * 获取岛屿所有成员的 AuraSkills PowerLevel 明细和总和。
     * <p>
     * 在线玩家直接读取，离线玩家通过 {@code loadUser()} 异步加载。
     * 使用 {@link CompletableFuture#allOf(CompletableFuture[])} 等待所有离线加载完成。
     *
     * @param island 目标岛屿
     * @return 包含全体成员 PowerLevel 明细和总和的 {@link AuraSkillsIslandResult}
     */
    public static CompletableFuture<AuraSkillsIslandResult> getIslandResult(Island island) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(new AuraSkillsIslandResult(0, List.of()));
        }

        AuraSkillsApi api = AuraSkillsApi.get();
        List<UUID> allUuids = new ArrayList<>();

        // 岛主
        allUuids.add(island.getOwnerId());
        // 成员
        allUuids.addAll(island.getMembers().keySet());

        if (allUuids.isEmpty()) {
            return CompletableFuture.completedFuture(new AuraSkillsIslandResult(0, List.of()));
        }

        List<CompletableFuture<Void>> asyncLoads = new ArrayList<>();
        int[] powerLevels = new int[allUuids.size()];

        for (int i = 0; i < allUuids.size(); i++) {
            UUID uuid = allUuids.get(i);
            Player onlinePlayer = Bukkit.getPlayer(uuid);

            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                // 在线玩家 —— 直接同步读取
                SkillsUser user = api.getUser(uuid);
                powerLevels[i] = user != null ? user.getPowerLevel() : 0;
            } else {
                // 离线玩家 —— 异步加载
                int index = i;
                CompletableFuture<Void> future = api.getUserManager().loadUser(uuid)
                        .thenAccept(user -> powerLevels[index] = user != null ? user.getPowerLevel() : 0);
                asyncLoads.add(future);
            }
        }

        CompletableFuture<Void> allDone = asyncLoads.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(asyncLoads.toArray(new CompletableFuture<?>[0]));

        return allDone.thenApply(v -> {
            int total = 0;
            List<MemberSkillData> memberDataList = new ArrayList<>();

            for (int i = 0; i < allUuids.size(); i++) {
                UUID uuid = allUuids.get(i);
                int pl = powerLevels[i];
                total += pl;

                // 获取玩家名（在线优先，离线回退）
                Player onlinePlayer = Bukkit.getPlayer(uuid);
                String playerName = onlinePlayer != null && onlinePlayer.isOnline()
                        ? onlinePlayer.getName()
                        : getOfflinePlayerName(uuid);

                memberDataList.add(new MemberSkillData(playerName, pl));
            }

            return new AuraSkillsIslandResult(total, memberDataList);
        });
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