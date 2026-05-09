package team.starm.starmskyblock.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InvitationManager {

    private final IslandManager islandManager;
    private final ConfigManager configManager;
    private final SkyblockWorldManager worldManager;

    private final Map<UUID, InvitationData> pendingInvitations = new HashMap<>();

    public InvitationManager(IslandManager islandManager, ConfigManager configManager, SkyblockWorldManager worldManager) {
        this.islandManager = islandManager;
        this.configManager = configManager;
        this.worldManager = worldManager;
    }

    public static class InvitationData {
        private final UUID inviterUuid;
        private final int islandId;
        private final long timestamp;

        public InvitationData(UUID inviterUuid, int islandId) {
            this.inviterUuid = inviterUuid;
            this.islandId = islandId;
            this.timestamp = System.currentTimeMillis();
        }

        public UUID getInviterUuid() {
            return inviterUuid;
        }

        public int getIslandId() {
            return islandId;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 5 * 60 * 1000;
        }
    }

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
                MessageUtil.sendMessage(inviter, "&a已向 &e" + target.getName() + " &a发送岛屿邀请！");
            } else {
                MessageUtil.sendMessage(inviter, "&a已发送岛屿邀请！");
            }
        }

        if (target != null) {
            if (inviter != null) {
                MessageUtil.sendMessage(target, "&a你收到了来自 &e" + inviter.getName() + " &a的岛屿邀请！");
            } else {
                MessageUtil.sendMessage(target, "&a你收到了岛屿邀请！");
            }
            MessageUtil.sendMessage(target, "&a使用 &e/is accept &a接受邀请，或 &e/is decline &a拒绝邀请");
            MessageUtil.sendMessage(target, "&7邀请将在5分钟后过期");
        }

        return true;
    }

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

        if (islandManager.addMemberToIsland(invitation.getIslandId(), targetUuid, IslandPermissionLevel.MEMBER)) {
            Optional<Island> island = islandManager.getIsland(invitation.getIslandId());
            if (island.isPresent()) {
                Player target = Bukkit.getPlayer(targetUuid);
                if (target != null) {
                    Island islandObj = island.get();
                    Player inviter = Bukkit.getPlayer(invitation.getInviterUuid());

                    if (inviter != null) {
                        MessageUtil.sendMessage(inviter, "&a玩家 &e" + target.getName() + " &a已接受你的岛屿邀请！");
                    }

                    MessageUtil.sendMessage(target, "&a你已成功加入岛屿！正在传送...");

                    double[] offsets = configManager.getTeleportOffsetsBySchematicAndWorldType(
                            islandObj.getSchematicId(), Island.WorldType.NORMAL);
                    double teleportX = (islandObj.getCenterChunkX() * 16) + 8 + offsets[0];
                    double teleportY = configManager.getIslandHeight() + offsets[1];
                    double teleportZ = (islandObj.getCenterChunkZ() * 16) + 8 + offsets[2];

                    target.teleport(new Location(worldManager.getSkyblockWorld(), teleportX, teleportY, teleportZ));
                    MessageUtil.sendMessage(target, "&a欢迎来到岛屿！");
                }
            }

            pendingInvitations.remove(targetUuid);
            return true;
        }

        pendingInvitations.remove(targetUuid);
        return false;
    }

    public boolean declineInvitation(UUID targetUuid) {
        InvitationData invitation = pendingInvitations.get(targetUuid);
        if (invitation == null) {
            return false;
        }

        Player target = Bukkit.getPlayer(targetUuid);
        Player inviter = Bukkit.getPlayer(invitation.getInviterUuid());

        if (target != null) {
            MessageUtil.sendMessage(target, "&c你已拒绝岛屿邀请");
        }

        if (inviter != null) {
            if (target != null) {
                MessageUtil.sendMessage(inviter, "&c玩家 &e" + target.getName() + " &c拒绝了你的岛屿邀请");
            } else {
                MessageUtil.sendMessage(inviter, "&c玩家拒绝了你的岛屿邀请");
            }
        }

        pendingInvitations.remove(targetUuid);
        return true;
    }

    public InvitationData getPendingInvitation(UUID targetUuid) {
        InvitationData invitation = pendingInvitations.get(targetUuid);
        if (invitation != null && invitation.isExpired()) {
            pendingInvitations.remove(targetUuid);
            return null;
        }
        return invitation;
    }

    public void cleanupExpiredInvitations() {
        pendingInvitations.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public boolean hasPendingInvitation(UUID targetUuid) {
        InvitationData invitation = getPendingInvitation(targetUuid);
        return invitation != null;
    }
}
