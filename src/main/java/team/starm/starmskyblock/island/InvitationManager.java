package team.starm.starmskyblock.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InvitationManager {

  private final StarMSkyblock plugin;
  private final IslandManager islandManager;

  // 存储邀请数据：被邀请玩家UUID -> 邀请数据
  private final Map<UUID, InvitationData> pendingInvitations = new HashMap<>();

  public InvitationManager(StarMSkyblock plugin, IslandManager islandManager) {
    this.plugin = plugin;
    this.islandManager = islandManager;
  }

  /**
   * 邀请数据类
   */
  public static class InvitationData {
    private final UUID inviterUuid; // 邀请者UUID
    private final int islandId; // 岛屿ID
    private final long timestamp; // 邀请时间戳

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

    /**
     * 检查邀请是否过期（5分钟过期）
     */
    public boolean isExpired() {
      return System.currentTimeMillis() - timestamp > 5 * 60 * 1000; // 5分钟
    }
  }

  /**
   * 发送岛屿邀请
   */
  public boolean sendInvitation(UUID inviterUuid, UUID targetUuid, int islandId) {
    // 检查目标玩家是否已有岛屿
    Optional<Island> targetIsland = islandManager.getIsland(targetUuid);
    if (targetIsland.isPresent()) {
      return false;
    }

    // 检查是否已有待处理的邀请
    if (pendingInvitations.containsKey(targetUuid)) {
      InvitationData existingInvitation = pendingInvitations.get(targetUuid);
      if (!existingInvitation.isExpired()) {
        return false; // 已有未过期的邀请
      }
    }

    // 创建新邀请
    InvitationData invitation = new InvitationData(inviterUuid, islandId);
    pendingInvitations.put(targetUuid, invitation);

    // 发送消息给双方玩家
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

  /**
   * 接受邀请
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

    // 检查目标玩家是否已有岛屿
    Optional<Island> targetIsland = islandManager.getIsland(targetUuid);
    if (targetIsland.isPresent()) {
      pendingInvitations.remove(targetUuid);
      return false;
    }

    // 添加玩家到岛屿
    if (islandManager.addMemberToIsland(invitation.getIslandId(), targetUuid, IslandPermissionLevel.MEMBER)) {
      // 传送玩家到岛屿
      Optional<Island> island = islandManager.getIsland(invitation.getIslandId());
      if (island.isPresent()) {
        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null) {
          // 获取岛屿主世界传送点
          Island islandObj = island.get();
          Player inviter = Bukkit.getPlayer(invitation.getInviterUuid());

          if (inviter != null) {
            MessageUtil.sendMessage(inviter, "&a玩家 &e" + target.getName() + " &a已接受你的岛屿邀请！");
          }

          MessageUtil.sendMessage(target, "&a你已成功加入岛屿！正在传送...");

          // 使用岛屿的传送逻辑
          SkyblockWorldManager worldManager = plugin.getWorldManager();
          ConfigManager config = plugin.getConfigManager();

          // 获取主世界传送点
          double[] offsets = config.getTeleportOffsetsBySchematicAndWorldType(
              islandObj.getSchematicId(), Island.WorldType.NORMAL);
          double teleportX = (islandObj.getCenterChunkX() * 16) + 8 + offsets[0];
          double teleportY = config.getIslandHeight() + offsets[1];
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

  /**
   * 拒绝邀请
   */
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

  /**
   * 获取玩家的待处理邀请
   */
  public InvitationData getPendingInvitation(UUID targetUuid) {
    InvitationData invitation = pendingInvitations.get(targetUuid);
    if (invitation != null && invitation.isExpired()) {
      pendingInvitations.remove(targetUuid);
      return null;
    }
    return invitation;
  }

  /**
   * 清理过期的邀请
   */
  public void cleanupExpiredInvitations() {
    pendingInvitations.entrySet().removeIf(entry -> entry.getValue().isExpired());
  }

  /**
   * 检查玩家是否有待处理的邀请
   */
  public boolean hasPendingInvitation(UUID targetUuid) {
    InvitationData invitation = getPendingInvitation(targetUuid);
    return invitation != null;
  }
}
