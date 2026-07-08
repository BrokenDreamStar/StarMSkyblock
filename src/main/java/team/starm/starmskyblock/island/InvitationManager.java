package team.starm.starmskyblock.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邀请管理器 —— 处理玩家之间发送/接受/拒绝岛屿邀请。
 * <p>
 * 邀请有 5 分钟有效期，过期自动失效。
 * 被邀请的玩家如果有岛屿或已有有效邀请则不能再次被邀请。
 * 接受邀请时自动解除该玩家的合作者关系（如果存在）。
 */
public class InvitationManager {

    private final IslandManager islandManager;
    private final ConfigManager configManager;
    private final SkyblockWorldManager worldManager;

    /** 待处理的邀请缓存（目标玩家 UUID → 邀请数据） */
    private final Map<UUID, InvitationData> pendingInvitations = new ConcurrentHashMap<>();

    public InvitationManager(IslandManager islandManager, ConfigManager configManager, SkyblockWorldManager worldManager) {
        this.islandManager = islandManager;
        this.configManager = configManager;
        this.worldManager = worldManager;
    }

    /** 邀请数据：包含邀请人、目标岛屿 ID 和创建时间 */
    public record InvitationData(UUID inviterUuid, int islandId, long timestamp) {
        public InvitationData(UUID inviterUuid, int islandId) {
            this(inviterUuid, islandId, System.currentTimeMillis());
        }

        /** 判断邀请是否超过 5 分钟有效期 */
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 5 * 60 * 1000;
        }
    }

    /**
     * 发送岛屿邀请。
     * 前提条件：目标玩家没有岛屿，且没有未过期的待处理邀请。
     * 发送成功后双方都会收到消息提示。
     *
     * @return true 邀请发送成功
     */
    public boolean sendInvitation(UUID inviterUuid, UUID targetUuid, int islandId) {
        Optional<Island> targetIsland = islandManager.getIsland(targetUuid);
        if (targetIsland.isPresent()) {
            return false;
        }

        if (pendingInvitations.containsKey(targetUuid)) {
            InvitationData existingInvitation = pendingInvitations.get(targetUuid);
            if (!existingInvitation.isExpired()) {
                return false;
            }
        }

        InvitationData invitation = new InvitationData(inviterUuid, islandId);
        pendingInvitations.put(targetUuid, invitation);

        Player inviter = Bukkit.getPlayer(inviterUuid);
        Player target = Bukkit.getPlayer(targetUuid);

        if (inviter != null) {
            if (target != null) {
                MessageUtil.send(inviter, "island.invitation.sent", Map.of("player", target.getName()));
            } else {
                MessageUtil.send(inviter, "island.invitation.sent-noname");
            }
        }

        if (target != null) {
            if (inviter != null) {
                MessageUtil.send(target, "island.invitation.received", Map.of("player", inviter.getName()));
            } else {
                MessageUtil.send(target, "island.invitation.received-anonymous");
            }
            MessageUtil.send(target, "island.invitation.prompt");
            MessageUtil.send(target, "island.invitation.expiry-note");
        }

        return true;
    }

    /**
     * 接受邀请：将玩家以 MEMBER 权限组加入岛屿并传送。
     * 如果玩家已有岛屿或邀请已过期则拒绝。
     *
     * @return true 加入成功
     */
    public boolean acceptInvitation(UUID targetUuid) {
        InvitationData invitation = pendingInvitations.get(targetUuid);
        if (invitation == null) {
            return false;
        }

        if (invitation.isExpired()) {
            pendingInvitations.remove(targetUuid);
            return false;
        }

        Optional<Island> targetIsland = islandManager.getIsland(targetUuid);
        if (targetIsland.isPresent()) {
            pendingInvitations.remove(targetUuid);
            return false;
        }

        if (islandManager.addMemberToIsland(invitation.islandId(), targetUuid, IslandPermissionLevel.MEMBER)) {
            Optional<Island> island = islandManager.getIsland(invitation.islandId());
            if (island.isPresent()) {
                Player target = Bukkit.getPlayer(targetUuid);
                if (target != null) {
                    Island islandObj = island.get();
                    Player inviter = Bukkit.getPlayer(invitation.inviterUuid());

                    if (inviter != null) {
                        MessageUtil.send(inviter, "island.invitation.accepted", Map.of("player", target.getName()));
                    }

                    MessageUtil.send(target, "island.invitation.join-success");

                    double[] offsets = configManager.getTeleportOffsetsBySchematicAndWorldType(
                            islandObj.getSchematicId(), Island.WorldType.NORMAL);
                    double teleportX = (islandObj.getCenterChunkX() * 16) + 8 + offsets[0];
                    double teleportY = configManager.getIslandHeight() + offsets[1];
                    double teleportZ = (islandObj.getCenterChunkZ() * 16) + 8 + offsets[2];

                    Location targetLocation = new Location(worldManager.getSkyblockWorld(), teleportX, teleportY, teleportZ,
                            (float) offsets[3], (float) offsets[4]);
                    target.teleport(targetLocation);
                    MessageUtil.send(target, "island.invitation.welcome");

                }
            }

            pendingInvitations.remove(targetUuid);
            return true;
        }

        pendingInvitations.remove(targetUuid);
        return false;
    }

    /** 拒绝邀请：双方收到提示后移除待处理记录 */
    public boolean declineInvitation(UUID targetUuid) {
        InvitationData invitation = pendingInvitations.get(targetUuid);
        if (invitation == null) {
            return false;
        }

        Player target = Bukkit.getPlayer(targetUuid);
        Player inviter = Bukkit.getPlayer(invitation.inviterUuid());

        if (target != null) {
            MessageUtil.send(target, "team.decline.success");
        }

        if (inviter != null) {
            if (target != null) {
                MessageUtil.send(inviter, "island.invitation.declined", Map.of("player", target.getName()));
            } else {
                MessageUtil.send(inviter, "island.invitation.declined-anonymous");
            }
        }

        pendingInvitations.remove(targetUuid);
        return true;
    }

    /** 获取目标玩家的待处理邀请（自动过滤已过期的） */
    public InvitationData getPendingInvitation(UUID targetUuid) {
        InvitationData invitation = pendingInvitations.get(targetUuid);
        if (invitation != null && invitation.isExpired()) {
            pendingInvitations.remove(targetUuid);
            return null;
        }
        return invitation;
    }

    /** 清理所有已过期的邀请 */
    public void cleanupExpiredInvitations() {
        pendingInvitations.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /** 判断目标玩家是否有未过期的待处理邀请 */
    public boolean hasPendingInvitation(UUID targetUuid) {
        InvitationData invitation = getPendingInvitation(targetUuid);
        return invitation != null;
    }
}
