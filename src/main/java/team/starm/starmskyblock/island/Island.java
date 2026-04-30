package team.starm.starmskyblock.island;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import team.starm.starmskyblock.config.PermissionConfigManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionLevel;

public class Island {

    public enum WorldType {
        NORMAL, NETHER, END
    }

    private final int id;
    private final UUID ownerId;
    private String name;
    private int radius;
    private int centerChunkX;
    private int centerChunkZ;
    private boolean showBorder;

    private double customHomeX;
    private double customHomeY;
    private double customHomeZ;
    private WorldType customHomeWorldType;
    private boolean hasCustomHome;

    private String schematicId;

    private final Map<UUID, IslandPermissionLevel> members = new HashMap<>();
    private final Map<IslandPermissionLevel, Set<IslandPermission>> customPermissions = new HashMap<>();

    private transient PermissionConfigManager permissionConfigManager;

    public Island(int id, UUID ownerId, int radius) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = "";
        this.radius = radius;
        this.showBorder = true;
        this.customHomeWorldType = WorldType.NORMAL;
        this.schematicId = "default";
    }

    public Island(int id, UUID ownerId, int radius, String schematicId) {
        this(id, ownerId, radius);
        this.schematicId = schematicId != null ? schematicId : "default";
    }

    public Island(int id, UUID ownerId, int radius, String schematicId, String name) {
        this(id, ownerId, radius, schematicId);
        this.name = name != null ? name : "";
    }

    public void setPermissionConfigManager(PermissionConfigManager permissionConfigManager) {
        this.permissionConfigManager = permissionConfigManager;
    }

    public int getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : "";
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public int getCenterChunkX() {
        return centerChunkX;
    }

    public void setCenterChunkX(int centerChunkX) {
        this.centerChunkX = centerChunkX;
    }

    public int getCenterChunkZ() {
        return centerChunkZ;
    }

    public void setCenterChunkZ(int centerChunkZ) {
        this.centerChunkZ = centerChunkZ;
    }

    public boolean isShowBorder() {
        return showBorder;
    }

    public void setShowBorder(boolean showBorder) {
        this.showBorder = showBorder;
    }

    public Map<UUID, IslandPermissionLevel> getMembers() {
        return new HashMap<>(members);
    }

    public void addMember(UUID uuid, IslandPermissionLevel role) {
        members.put(uuid, role);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public void setMemberRole(UUID uuid, IslandPermissionLevel role) {
        if (members.containsKey(uuid)) {
            members.put(uuid, role);
        }
    }

    public IslandPermissionLevel getMemberRole(UUID uuid) {
        if (ownerId.equals(uuid)) {
            return IslandPermissionLevel.OWNER;
        }
        return members.getOrDefault(uuid, IslandPermissionLevel.VISITOR);
    }

    public boolean isChunkWithinIsland(int chunkX, int chunkZ) {
        return Math.abs(chunkX - centerChunkX) <= radius && Math.abs(chunkZ - centerChunkZ) <= radius;
    }

    public boolean hasCustomHome() {
        return hasCustomHome;
    }

    public void setCustomHome(WorldType worldType, double x, double y, double z) {
        this.customHomeX = x;
        this.customHomeY = y;
        this.customHomeZ = z;
        this.customHomeWorldType = worldType;
        this.hasCustomHome = true;
    }

    public void clearCustomHome() {
        this.hasCustomHome = false;
    }

    public double getCustomHomeX() {
        return customHomeX;
    }

    public double getCustomHomeY() {
        return customHomeY;
    }

    public double getCustomHomeZ() {
        return customHomeZ;
    }

    public WorldType getCustomHomeWorldType() {
        return customHomeWorldType;
    }

    public String getSchematicId() {
        return schematicId;
    }

    public void setSchematicId(String schematicId) {
        this.schematicId = schematicId != null ? schematicId : "default";
    }

    // ==================== 权限核心方法（强化版） ====================
    public boolean hasPermission(UUID playerUuid, IslandPermission permission) {
        IslandPermissionLevel playerRole = getMemberRole(playerUuid);
        return hasPermission(playerRole, permission);
    }

    public boolean hasPermission(IslandPermissionLevel role, IslandPermission permission) {
        // 岛主永远拥有全部权限（最高优先级硬保）
        if (role == IslandPermissionLevel.OWNER) {
            return true;
        }

        // 自定义权限优先
        Set<IslandPermission> customPerms = customPermissions.get(role);
        if (customPerms != null) {
            if (customPerms.contains(IslandPermission.ALL)) {
                return true;
            }
            return customPerms.contains(permission);
        }

        // 回退到默认配置
        if (permissionConfigManager != null) {
            return permissionConfigManager.hasDefaultPermission(role, permission);
        }

        return false;
    }

    public void addPermission(IslandPermissionLevel role, IslandPermission permission) {
        Set<IslandPermission> permissions = customPermissions.computeIfAbsent(role, k -> new HashSet<>());
        permissions.add(permission);
    }

    public void removePermission(IslandPermissionLevel role, IslandPermission permission) {
        Set<IslandPermission> permissions = customPermissions.get(role);
        if (permissions != null) {
            permissions.remove(permission);
            if (permissions.isEmpty()) {
                customPermissions.remove(role);
            }
        }
    }

    public Set<IslandPermission> getCustomPermissions(IslandPermissionLevel role) {
        return new HashSet<>(customPermissions.getOrDefault(role, new HashSet<>()));
    }

    public void resetPermissions(IslandPermissionLevel role) {
        customPermissions.remove(role);
    }

    public Map<IslandPermissionLevel, Set<IslandPermission>> getCustomPermissions() {
        return new HashMap<>(customPermissions);
    }
}