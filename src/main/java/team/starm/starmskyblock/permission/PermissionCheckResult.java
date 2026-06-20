package team.starm.starmskyblock.permission;

import java.util.UUID;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;

/**
 * 一次权限解析的不可变快照。
 * <p>
 * 捕获 Location→玩家/岛屿/锁定区域/公共区域的判定结果,由
 * {@link BasePermissionManager#resolve} 产出,供 {@link BasePermissionManager#check}
 * 多次复用。同一事件内多次权限检查共享同一 result 可避免重复执行
 * Bukkit.getPlayer / island grid 索引 / chunk 坐标计算。
 */
public final class PermissionCheckResult {

    private final UUID uuid;
    private final Island island;
    private final boolean onUnlockedIsland;
    private final boolean inPublicWorld;
    private final boolean outsideSkyblockWorld;
    private final boolean bypass;

    private PermissionCheckResult(UUID uuid, Island island, boolean onUnlockedIsland,
                                  boolean inPublicWorld, boolean outsideSkyblockWorld, boolean bypass) {
        this.uuid = uuid;
        this.island = island;
        this.onUnlockedIsland = onUnlockedIsland;
        this.inPublicWorld = inPublicWorld;
        this.outsideSkyblockWorld = outsideSkyblockWorld;
        this.bypass = bypass;
    }

    static PermissionCheckResult bypass(Player player) {
        return new PermissionCheckResult(player.getUniqueId(), null, false, false, false, true);
    }

    static PermissionCheckResult outsideSkyblockWorld(Player player, boolean inPublicWorld) {
        return new PermissionCheckResult(player.getUniqueId(), null, false, inPublicWorld, !inPublicWorld, false);
    }

    static PermissionCheckResult outsideSkyblockWorld(UUID uuid, boolean inPublicWorld) {
        return new PermissionCheckResult(uuid, null, false, inPublicWorld, !inPublicWorld, false);
    }

    static PermissionCheckResult publicArea(Player player) {
        return new PermissionCheckResult(player.getUniqueId(), null, false, true, false, false);
    }

    static PermissionCheckResult publicArea(UUID uuid) {
        return new PermissionCheckResult(uuid, null, false, true, false, false);
    }

    static PermissionCheckResult onIsland(Player player, Island island) {
        return new PermissionCheckResult(player.getUniqueId(), island, true, false, false, false);
    }

    static PermissionCheckResult onIsland(UUID uuid, Island island) {
        return new PermissionCheckResult(uuid, island, true, false, false, false);
    }

    static PermissionCheckResult lockedArea(Player player, Island island) {
        return new PermissionCheckResult(player.getUniqueId(), island, false, false, false, false);
    }

    static PermissionCheckResult lockedArea(UUID uuid, Island island) {
        return new PermissionCheckResult(uuid, island, false, false, false, false);
    }

    public UUID uuid() { return uuid; }
    public Island island() { return island; }
    public boolean onUnlockedIsland() { return onUnlockedIsland; }
    public boolean inPublicWorld() { return inPublicWorld; }
    public boolean outsideSkyblockWorld() { return outsideSkyblockWorld; }
    public boolean bypass() { return bypass; }
}
