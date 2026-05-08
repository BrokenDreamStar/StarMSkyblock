package team.starm.starmskyblock.island;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
    private int maxRadius;
    private int centerChunkX;
    private int centerChunkZ;

    private double customHomeX;
    private double customHomeY;
    private double customHomeZ;
    private WorldType customHomeWorldType;
    private boolean hasCustomHome;

    private int level;
    private String settings;

    private String schematicId;

    private final Map<UUID, IslandPermissionLevel> members = new HashMap<>();
    private final Map<IslandPermission, Integer> permissionMinLevels = new HashMap<>();

    private static final Gson GSON = new GsonBuilder().create();

    public Island(int id, UUID ownerId, int radius) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = "";
        this.radius = radius;
        this.customHomeWorldType = WorldType.NORMAL;
        this.schematicId = "default";
        this.settings = new IslandSettings().toJson();
        // 兜底初始化：所有权限默认仅岛主，之后会被 getDefaultMinLevels() 覆盖
        for (IslandPermission perm : IslandPermission.values()) {
            this.permissionMinLevels.put(perm, IslandPermissionLevel.OWNER.getPermissionLevel());
        }
    }

    public Island(int id, UUID ownerId, int radius, String schematicId) {
        this(id, ownerId, radius);
        this.schematicId = schematicId != null ? schematicId : "default";
    }

    public Island(int id, UUID ownerId, int radius, String schematicId, String name) {
        this(id, ownerId, radius, schematicId);
        this.name = name != null ? name : "";
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

    public int getMaxRadius() {
        return maxRadius;
    }

    public void setMaxRadius(int maxRadius) {
        this.maxRadius = maxRadius;
    }

    public int getEffectiveMaxRadius() {
        return maxRadius > 0 ? maxRadius : radius;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getSettings() {
        return settings != null ? settings : "{}";
    }

    public void setSettings(String settings) {
        this.settings = settings != null ? settings : "{}";
    }

    public IslandSettings getSettingsObject() {
        return IslandSettings.fromJson(getSettings());
    }

    public void applySettings(IslandSettings islandSettings) {
        this.settings = islandSettings.toJson();
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

    public boolean isChunkWithinMaxRange(int chunkX, int chunkZ) {
        int effectiveMax = getEffectiveMaxRadius();
        return Math.abs(chunkX - centerChunkX) <= effectiveMax && Math.abs(chunkZ - centerChunkZ) <= effectiveMax;
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

    // ==================== 权限核心方法（最低等级模式） ====================
    public boolean hasPermission(UUID playerUuid, IslandPermission permission) {
        IslandPermissionLevel playerRole = getMemberRole(playerUuid);
        return hasPermission(playerRole, permission);
    }

    public boolean hasPermission(IslandPermissionLevel role, IslandPermission permission) {
        // 岛主永远拥有全部权限
        if (role == IslandPermissionLevel.OWNER) {
            return true;
        }

        // 检查该权限的自定义最低等级（如 BUILD→3 表示 ≥3 级拥有）
        Integer minLevel = permissionMinLevels.get(permission);
        if (minLevel != null) {
            return role.getPermissionLevel() >= minLevel;
        }

        // ALL 兜底：未单独配置的权限以此为准
        Integer allMinLevel = permissionMinLevels.get(IslandPermission.ALL);
        return allMinLevel != null && role.getPermissionLevel() >= allMinLevel;
    }

    // ==================== 自定义最低等级操作 ====================

    public void setPermissionMinLevel(IslandPermission permission, int minLevel) {
        permissionMinLevels.put(permission, minLevel);
    }

    public Integer getPermissionMinLevel(IslandPermission permission) {
        return permissionMinLevels.get(permission);
    }

    public void removePermissionMinLevel(IslandPermission permission) {
        permissionMinLevels.remove(permission);
    }

    public Map<IslandPermission, Integer> getPermissionMinLevels() {
        return new HashMap<>(permissionMinLevels);
    }

    // ==================== JSON 序列化（所有权限持久化） ====================

    /**
     * 将权限最低等级写入 JSON（使用 String 键避免枚举序列化问题）
     */
    public String getPermissionsJson() {
        Map<String, Integer> stringMap = new java.util.LinkedHashMap<>();
        for (IslandPermission perm : IslandPermission.values()) {
            Integer level = permissionMinLevels.get(perm);
            if (level != null) {
                stringMap.put(perm.name(), level);
            }
        }
        return GSON.toJson(stringMap);
    }

    /**
     * 从 JSON 加载权限最低等级配置。值为 null 或缺失的权限使用默认配置。
     */
    public void setPermissionsFromJson(String json) {
        permissionMinLevels.clear();
        if (json == null || json.isEmpty() || json.equals("{}")) return;
        try {
            Map<String, Object> raw = GSON.fromJson(json, Map.class);
            if (raw == null) return;
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                try {
                    IslandPermission perm = IslandPermission.valueOf(entry.getKey());
                    Object value = entry.getValue();
                    if (value instanceof Number) {
                        permissionMinLevels.put(perm, ((Number) value).intValue());
                    }
                } catch (IllegalArgumentException ignored) {
                    // 跳过未知权限键
                }
            }
        } catch (Exception ignored) {
            // 忽略 JSON 解析错误
        }
    }
}
