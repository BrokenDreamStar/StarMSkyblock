package team.starm.starmskyblock.island;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;

import team.starm.starmskyblock.message.MessageUtil;
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
     * 自定义传送点偏航角（水平朝向）
     */
    private float customHomeYaw;
    /**
     * 自定义传送点俯仰角（垂直朝向）
     */
    private float customHomePitch;
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
     * 下界是否已解锁（有岛屿成员进入过下界后为 true）
     */
    private boolean netherUnlocked = false;

    /**
     * 岛屿创建时间（ISO-8601 格式，来自 SQLite CURRENT_TIMESTAMP）
     */
    private String createdAt;

    /**
     * 岛屿等级
     */
    private int level;

    /**
     * 岛屿累计经验值（用于等级换算）
     */
    private double experience;

    /**
     * 岛屿上每种方块的数量统计（Material 名 → 数量）
     */
    private Map<String, Long> blockCounts = new HashMap<>();

    /**
     * 模板基线经验值（创建岛屿时模板方块的累计经验值）
     */
    private double baselineExperience;

    /**
     * 模板基线方块计数（创建岛屿时模板方块的数量，Material 名 → 数量）
     */
    private Map<String, Long> baselineBlockCounts = new HashMap<>();

    /**
     * 刷石机生成器等级（决定刷石机替换概率，与岛屿等级独立）
     */
    private int generatorLevel = 1;

    /**
     * AuraSkills 加成等级（全体成员 PowerLevel 总和 / coefficient）
     */
    private int auraskillsContribution = 0;

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

    private static final Gson GSON = new Gson();

    /**
     * 刷石机已禁用的矿石（维度名 → 禁用矿石 Material 名集合）
     */
    private final Map<String, Set<String>> disabledGeneratorOres = new HashMap<>();

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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public double getExperience() {
        return experience;
    }

    public void setExperience(double experience) {
        this.experience = experience;
    }

    /**
     * @return 方块计数 Map（Material 名 → 数量），不可变副本
     */
    public Map<String, Long> getBlockCounts() {
        return Collections.unmodifiableMap(blockCounts);
    }

    /**
     * 设置方块计数 Map
     */
    public void setBlockCounts(Map<Material, Long> counts) {
        this.blockCounts = new HashMap<>();
        for (Map.Entry<Material, Long> entry : counts.entrySet()) {
            this.blockCounts.put(entry.getKey().name(), entry.getValue());
        }
    }

    /**
     * 从反序列化的 String→Long Map 恢复方块计数
     */
    public void setBlockCountsFromRaw(Map<String, Long> counts) {
        this.blockCounts = new HashMap<>(counts);
    }

    // ==================== 模板基线（Baseline） ====================

    public double getBaselineExperience() {
        return baselineExperience;
    }

    public void setBaselineExperience(double baselineExperience) {
        this.baselineExperience = baselineExperience;
    }

    public Map<String, Long> getBaselineBlockCounts() {
        return Collections.unmodifiableMap(baselineBlockCounts);
    }

    public void setBaselineBlockCounts(Map<String, Long> counts) {
        this.baselineBlockCounts = new HashMap<>(counts);
    }

    /**
     * 累加基线方块计数（用于从 Clipboard 遍历时逐块累加）
     */
    public void addToBaseline(String materialName, long count) {
        baselineBlockCounts.merge(materialName, count, Long::sum);
    }

    /**
     * @return 刷石机生成器等级
     */
    public int getGeneratorLevel() {
        return generatorLevel;
    }

    /**
     * 设置刷石机生成器等级
     *
     * @param generatorLevel 新的生成器等级
     */
    public void setGeneratorLevel(int generatorLevel) {
        this.generatorLevel = generatorLevel;
    }

    /**
     * @return AuraSkills 加成等级
     */
    public int getAuraSkillsContribution() {
        return auraskillsContribution;
    }

    /**
     * 设置 AuraSkills 加成等级
     */
    public void setAuraSkillsContribution(int auraskillsContribution) {
        this.auraskillsContribution = auraskillsContribution;
    }

    /**
     * @return 已禁用的刷石机矿石（维度名 → 禁用矿石 Material 名集合），不可变副本
     */
    public Map<String, Set<String>> getDisabledGeneratorOres() {
        Map<String, Set<String>> copy = new HashMap<>();
        for (var entry : disabledGeneratorOres.entrySet()) {
            copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;
    }

    /**
     * 切换指定维度中某个矿石的启用/禁用状态。
     *
     * @return 操作后的状态：true=启用（不在禁用集中），false=禁用
     */
    public boolean toggleGeneratorOre(String dimension, String materialName, Boolean enable) {
        Set<String> disabled = disabledGeneratorOres.computeIfAbsent(dimension, k -> new HashSet<>());
        if (enable == null) {
            // toggle
            if (disabled.contains(materialName)) {
                disabled.remove(materialName);
                if (disabled.isEmpty()) disabledGeneratorOres.remove(dimension);
                return true;
            } else {
                disabled.add(materialName);
                return false;
            }
        } else if (enable) {
            disabled.remove(materialName);
            if (disabled.isEmpty()) disabledGeneratorOres.remove(dimension);
            return true;
        } else {
            disabled.add(materialName);
            return false;
        }
    }

    public boolean isGeneratorOreDisabled(String dimension, String materialName) {
        Set<String> disabled = disabledGeneratorOres.get(dimension);
        return disabled != null && disabled.contains(materialName);
    }

    public String getDisabledGeneratorOresJson() {
        if (disabledGeneratorOres.isEmpty()) return "{}";
        return GSON.toJson(disabledGeneratorOres);
    }

    @SuppressWarnings("unchecked")
    public void setDisabledGeneratorOresFromJson(String json) {
        disabledGeneratorOres.clear();
        if (json == null || json.isEmpty() || json.equals("{}")) return;
        try {
            Map<String, Object> raw = GSON.fromJson(json, Map.class);
            if (raw == null) return;
            for (var entry : raw.entrySet()) {
                if (entry.getValue() instanceof List<?> list) {
                    Set<String> set = new HashSet<>();
                    for (Object item : list) {
                        if (item instanceof String s) set.add(s);
                    }
                    if (!set.isEmpty()) disabledGeneratorOres.put(entry.getKey(), set);
                }
            }
        } catch (Exception e) {
            MessageUtil.consoleWarn("解析禁用生成器矿石 JSON 失败，岛屿 ID: " + id + "，数据: " + json);
        }
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
        return IslandSerializer.settingsToJson(settingValues);
    }

    public void setSettingsFromJson(String json) {
        IslandSerializer.settingsFromJson(json, settingValues);
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

    public void setCustomHome(WorldType worldType, double x, double y, double z, float yaw, float pitch) {
        this.customHomeX = x;
        this.customHomeY = y;
        this.customHomeZ = z;
        this.customHomeYaw = yaw;
        this.customHomePitch = pitch;
        this.customHomeWorldType = worldType;
        this.hasCustomHome = true;
        this.homeJson = HomeLocation.toJson(worldType, x, y, z, yaw, pitch);
    }

    public void clearCustomHome() {
        this.hasCustomHome = false;
        this.homeJson = "{}";
    }

    public boolean isNetherUnlocked() {
        return netherUnlocked;
    }

    public void setNetherUnlocked(boolean netherUnlocked) {
        this.netherUnlocked = netherUnlocked;
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

    public float getCustomHomeYaw() {
        return customHomeYaw;
    }

    public float getCustomHomePitch() {
        return customHomePitch;
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
     * 判断某玩家是否拥有某权限（基于其身份在岛屿中的权限组）。
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
     * 基于权限组的等级数值判断是否拥有指定权限。
     * 岛主全通过；其他权限组需达到该权限在岛屿中配置的最低等级，未配置则拒绝。
     */
    public boolean hasPermission(IslandPermissionLevel role, IslandPermission permission) {
        // 委托给 IslandPermissionLevel.hasPermission（可单测）；岛主全通过，否则看该权限配置的最低等级
        return role.hasPermission(permission, permissionMinLevels.get(permission));
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

    public String getPermissionsJson() {
        return IslandSerializer.permissionsToJson(permissionMinLevels);
    }

    public void setPermissionsFromJson(String json) {
        IslandSerializer.permissionsFromJson(json, permissionMinLevels);
    }
}
