package team.starm.starmskyblock.island;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.setting.IslandSetting;

/**
 * 岛屿领域模型 —— 封装一个天空岛屿的全部数据和行为。
 * <p>
 * 每个实例对应数据库中 islands 表的一条记录，包含：
 * <ul>
 *   <li>基础属性（ID、名称、岛主、半径、等级）</li>
 *   <li>空间位置（中心区块坐标、最大可扩展半径）</li>
 *   <li>成员与权限体系（成员、合作者、权限最低等级）</li>
 *   <li>岛屿设置（PVP、生物生成等开关）</li>
 *   <li>自定义传送点（跨世界）</li>
 * </ul>
 */
public class Island {

    /**
     * 世界类型枚举，用于区分主世界/下界/末地的传送点归属
     */
    public enum WorldType {
        NORMAL, NETHER, END
    }

    /**
     * 数据库自增主键
     */
    private final int id;
    /**
     * 岛主 UUID
     */
    private final UUID ownerId;
    /**
     * 岛屿显示名称
     */
    private String name;
    /**
     * 当前岛屿半径（区块数），决定了边界和成员可活动范围
     */
    private int radius;
    /**
     * 最大可扩展半径（区块数），0 表示未设上限
     */
    private int maxRadius;
    /**
     * 岛屿中心所在的区块 X 坐标
     */
    private int centerChunkX;
    /**
     * 岛屿中心所在的区块 Z 坐标
     */
    private int centerChunkZ;

    /**
     * 自定义传送点 X 坐标
     */
    private double customHomeX;
    /**
     * 自定义传送点 Y 坐标
     */
    private double customHomeY;
    /**
     * 自定义传送点 Z 坐标
     */
    private double customHomeZ;
    /**
     * 自定义传送点所在的世界类型
     */
    private WorldType customHomeWorldType;
    /**
     * 是否已设置自定义传送点
     */
    private boolean hasCustomHome;
    /**
     * 传送点数据的 JSON 序列化缓存
     */
    private String homeJson = "{}";

    /**
     * 岛屿等级
     */
    private int level;

    /**
     * 岛屿功能设置（枚举 → 布尔值），使用 EnumMap 保证紧凑存储
     */
    private final Map<IslandSetting, Boolean> settingValues = new EnumMap<>(IslandSetting.class);

    /**
     * 所使用的结构模板 ID（决定生成哪种岛屿样式）
     */
    private String schematicId;

    /**
     * 岛屿成员表（UUID → 权限等级）
     */
    private final Map<UUID, IslandPermissionLevel> members = new HashMap<>();
    /**
     * 合作者 UUID 集合
     */
    private final Set<UUID> coops = new HashSet<>();
    /**
     * 各项权限的最低等级要求（权限 → 最低等级数值）
     */
    private final Map<IslandPermission, Integer> permissionMinLevels = new HashMap<>();

    /**
     * 用于 JSON 序列化/反序列化的共享 Gson 实例
     */
    private static final Gson GSON = new GsonBuilder().create();

    /**
     * 构造岛屿核心模型（不含名称和结构 ID 的重载版本）。
     * 默认启用所有设置，权限默认仅岛主可操作。
     */
    public Island(int id, UUID ownerId, int radius) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = "";
        this.radius = radius;
        this.customHomeWorldType = WorldType.NORMAL;
        this.schematicId = "default";
        // 默认所有设置启用
        for (IslandSetting setting : IslandSetting.values()) {
            this.settingValues.put(setting, true);
        }
        // 兜底初始化：所有权限默认仅岛主，之后会被 getDefaultMinLevels() 覆盖
        for (IslandPermission perm : IslandPermission.values()) {
            this.permissionMinLevels.put(perm, IslandPermissionLevel.OWNER.getPermissionLevel());
        }
    }

    /**
     * 带结构 ID 的构造函数
     */
    public Island(int id, UUID ownerId, int radius, String schematicId) {
        this(id, ownerId, radius);
        this.schematicId = schematicId != null ? schematicId : "default";
    }

    /**
     * 完整参数的构造函数（含岛屿名称）
     */
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

    public boolean getSetting(IslandSetting setting) {
        return settingValues.getOrDefault(setting, true);
    }

    public void setSetting(IslandSetting setting, boolean value) {
        settingValues.put(setting, value);
    }

    public void applySettings(Map<IslandSetting, Boolean> defaults) {
        settingValues.putAll(defaults);
    }

    public String getSettingsJson() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (IslandSetting setting : IslandSetting.values()) {
            map.put(setting.name(), settingValues.get(setting));
        }
        return GSON.toJson(map);
    }

    /**
     * 从 JSON 加载设置。支持新格式 (枚举名大写) 和旧格式 (camelCase字段名)。
     */
    @SuppressWarnings("unchecked")
    public void setSettingsFromJson(String json) {
        if (json == null || json.isEmpty() || json.equals("{}")) return;
        try {
            Map<String, Object> raw = GSON.fromJson(json, Map.class);
            if (raw == null) return;
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (!(entry.getValue() instanceof Boolean)) continue;
                String key = entry.getKey();
                IslandSetting setting = null;
                // 尝试枚举名匹配（大小写不敏感）
                try {
                    setting = IslandSetting.valueOf(key.toUpperCase());
                } catch (IllegalArgumentException e) {
                    // 尝试旧格式 camelCase → 枚举名
                    setting = legacyKeyToSetting(key);
                }
                if (setting != null) {
                    settingValues.put(setting, (Boolean) entry.getValue());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static IslandSetting legacyKeyToSetting(String key) {
        return switch (key) {
            case "pvp" -> IslandSetting.PVP;
            case "animalSpawn" -> IslandSetting.ANIMAL_SPAWN;
            case "monsterSpawn" -> IslandSetting.MONSTER_SPAWN;
            case "spawnerSpawn" -> IslandSetting.SPAWNER_SPAWN;
            case "fireSpread" -> IslandSetting.FIRE_SPREAD;
            case "endermanGrief" -> IslandSetting.ENDERMAN_GRIEF;
            case "ghastFireballGrief" -> IslandSetting.GHAST_FIREBALL_GRIEF;
            case "creeperExplosion" -> IslandSetting.CREEPER_EXPLOSION;
            case "tntExplosion" -> IslandSetting.TNT_EXPLOSION;
            case "witherGrief" -> IslandSetting.WITHER_GRIEF;
            default -> null;
        };
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

    public boolean isCoop(UUID uuid) {
        return coops.contains(uuid);
    }

    public Set<UUID> getCoops() {
        return new HashSet<>(coops);
    }

    public void addCoop(UUID uuid) {
        coops.add(uuid);
    }

    public void removeCoop(UUID uuid) {
        coops.remove(uuid);
    }

    public IslandPermissionLevel getMemberRole(UUID uuid) {
        if (ownerId.equals(uuid)) {
            return IslandPermissionLevel.OWNER;
        }
        return members.getOrDefault(uuid, IslandPermissionLevel.VISITOR);
    }

    /**
     * 判断指定区块坐标是否在当前岛屿半径范围内
     */
    public boolean isChunkWithinIsland(int chunkX, int chunkZ) {
        return Math.abs(chunkX - centerChunkX) <= radius && Math.abs(chunkZ - centerChunkZ) <= radius;
    }

    /**
     * 判断指定区块坐标是否在最大可扩展半径范围内（用于边界显示和传送门限制）
     */
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
        this.homeJson = GSON.toJson(java.util.Map.of("world", worldType.name(), "x", x, "y", y, "z", z));
    }

    public void clearCustomHome() {
        this.hasCustomHome = false;
        this.homeJson = "{}";
    }

    public String getHomeJson() {
        return homeJson;
    }

    public void setHomeFromJson(String json) {
        this.homeJson = (json != null && !json.isEmpty()) ? json : "{}";
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

    /**
     * 判断某玩家是否拥有某权限（基于其身份在岛屿中的角色）。
     * 权限检查链路：岛主 → 成员 → 合作者 → 访客。
     */
    public boolean hasPermission(UUID playerUuid, IslandPermission permission) {
        if (ownerId.equals(playerUuid)) return true;
        IslandPermissionLevel role = members.get(playerUuid);
        if (role != null) return hasPermission(role, permission);
        if (coops.contains(playerUuid)) return hasPermission(IslandPermissionLevel.COOP, permission);
        return hasPermission(IslandPermissionLevel.VISITOR, permission);
    }

    /**
     * 基于角色的等级数值判断是否拥有指定权限。
     * 先检查该权限的自定义最低等级，未单独配置则回退为 ALL 的兜底值。
     */
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

        return false;
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
    @SuppressWarnings("unchecked")
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
