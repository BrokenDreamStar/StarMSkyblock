# 公共区域权限与设置配置 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为空岛世界中所有不归属于任何岛屿的区域（虚空、岛屿间隙、世界出生点）添加独立的权限和设置保护。

**Architecture:** 新建 `public-area.yml` 配置文件 + `PublicAreaConfigManager`，采用布尔值（允许/禁止）而非角色等级体系。在 `BasePermissionManager.checkPermission()` 和 `BaseSettingManager.checkSetting()` 中注入公共区域回退逻辑。同时注册现有的 `IslandBoundaryListener` 以保护环境边界。

**Tech Stack:** Spigot 1.26.x, Paper, Bukkit API, YamlConfiguration

---

### Task 1: 创建 public-area.yml 配置文件

**Files:**
- Create: `src/main/resources/public-area.yml`

- [ ] **Step 1: 创建 public-area.yml**

```yaml
# 公共区域权限与设置配置
# 适用于空岛世界中所有不归属于任何岛屿的区域
# true = 允许/启用, false = 禁止/禁用

# 是否启用公共区域保护
enabled: true

# 权限定义
permissions:
  # 管理权限（公共区域禁止所有管理操作）
  RENAME_ISLAND: false
  EDIT_PERMISSIONS: false
  EDIT_SETTINGS: false
  INVITE_MEMBER: false
  REMOVE_MEMBER: false
  SET_ROLE: false
  INVITE_COOP: false
  REMOVE_COOP: false
  SET_SPAWN: false
  SET_BIOME: false
  SET_GENERATOR: false

  # 物品丢弃/拾取
  ITEM_DROP: true
  ITEM_PICKUP: true
  EXP_PICKUP: true

  # 方块破坏/建造
  BREAK: false
  BUILD: false

  # 工作方块
  CRAFTING_TABLE_USE: false
  ENCHANTING_TABLE_USE: false
  BEACON_USE: false
  ANVIL_USE: false
  GRINDSTONE_USE: false
  CARTOGRAPHY_TABLE_USE: false
  STONECUTTER_USE: false
  LOOM_USE: false
  SMITHING_TABLE_USE: false
  CAMPFIRE_USE: false

  # 容器
  FURNACE_OPEN: false
  CHEST_OPEN: false
  BARREL_OPEN: false
  ENDER_CHEST_OPEN: true
  SHULKER_BOX_OPEN: false
  HOPPER_OPEN: false
  DISPENSER_OPEN: false
  DROPPER_OPEN: false
  CRAFTER_OPEN: false
  BREWING_STAND_OPEN: false
  SHELF_USE: false
  ITEM_FRAME_USE: false
  JUKEBOX_USE: false
  LECTERN_USE: false
  CHISELED_BOOKSHELF_USE: false
  DECORATED_POT_USE: false
  COMPOSTER_USE: false
  FLOWER_POT_USE: false
  ANIMAL_INVENTORY_OPEN: false

  # 红石
  BUTTON_PRESS: false
  LEVER_USE: false
  REPEATER_USE: false
  COMPARATOR_USE: false
  DAYLIGHT_DETECTOR_USE: false
  PRESSURE_PLATE_TRIGGER: false
  TRIPWIRE_HOOK_TRIGGER: false
  SCULK_SENSOR_TRIGGER: false
  BELL_RING: false
  NOTE_BLOCK_USE: false

  # 门
  DOOR_OPEN: false
  FENCE_GATE_OPEN: false
  TRAPDOOR_OPEN: false

  # 载具
  MINECART_DAMAGE: false
  MINECART_ENTER: true
  MINECART_PLACE: false
  BOAT_DAMAGE: false
  BOAT_ENTER: true
  BOAT_PLACE: false

  # 工具
  BOW_USE: false
  AXE_USE: false
  SHOVEL_USE: false
  HOE_USE: false
  BUCKET_USE: false
  GLASS_BOTTLE_USE: false
  BOWL_USE: false
  FISHING_ROD_USE: true
  FLINT_AND_STEEL_USE: false
  SHEARS_USE: false
  BRUSH_USE: false
  LEASH_USE: false

  # 物品
  FIREWORK_USE: false
  NAME_TAG_USE: false
  POTION_THROW: false
  WATER_BOTTLE_USE: false
  BONE_MEAL_USE: false
  DYE_USE: false
  INK_SAC_USE: false
  HONEYCOMB_USE: false
  CHORUS_FRUIT_EAT: false
  ENDER_PEARL_USE: false
  ENDER_EYE_USE: false
  WIND_CHARGE_USE: false
  SNOWBALL_THROW: false
  EGG_THROW: false

  # 生物
  ANIMAL_FEED: false
  ENTITY_RIDE: true
  ENTITY_EQUIP: false
  ANIMAL_DAMAGE: false
  MONSTER_DAMAGE: true
  VILLAGER_DAMAGE: false
  VILLAGER_TRADE: false
  BARTERING: false
  ALLAY_INTERACT: false
  ARMOR_STAND_DAMAGE: false
  ARMOR_STAND_INTERACT: false

  # 其它
  ENTER_NETHER_PORTAL: true
  ENTER_END_PORTAL: true
  SPAWN_EGG_USE: false
  FARMLAND_TRAMPLE: false
  TURTLE_EGG_TRAMPLE: false
  SWEET_BERRY_HARVEST: false
  CAKE_EAT: false
  SIGN_EDIT: false
  BED_USE: false
  RESPAWN_ANCHOR_USE: false
  END_CRYSTAL_DAMAGE: false
  RAID_TRIGGER: false

# 设置定义
settings:
  PVP: false
  TP: true
  ANIMAL_SPAWN: true
  MONSTER_SPAWN: true
  SPAWNER_SPAWN: true
  FIRE_SPREAD: false
  ENDERMAN_GRIEF: false
  GHAST_FIREBALL_GRIEF: false
  CREEPER_EXPLOSION: false
  TNT_EXPLOSION: false
  WITHER_GRIEF: false
  PHANTOM_SPAWN: false
```

---

### Task 2: 创建 PublicAreaConfigManager

**Files:**
- Create: `src/main/java/team/starm/starmskyblock/config/PublicAreaConfigManager.java`

- [ ] **Step 1: 创建 PublicAreaConfigManager.java**

```java
package team.starm.starmskyblock.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.setting.IslandSetting;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

/**
 * 公共区域权限与设置管理器（public-area.yml）。
 * 定义空岛世界中所有非岛屿区域的默认权限和设置行为。
 */
public class PublicAreaConfigManager {

    private final StarMSkyblock plugin;
    private FileConfiguration config;
    private File configFile;

    private boolean enabled;
    private final Map<IslandPermission, Boolean> permissionDefaults = new EnumMap<>(IslandPermission.class);
    private final Map<IslandSetting, Boolean> settingDefaults = new EnumMap<>(IslandSetting.class);

    public PublicAreaConfigManager(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        loadConfig();
    }

    public void loadConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        configFile = new File(plugin.getDataFolder(), "public-area.yml");

        if (!configFile.exists()) {
            plugin.saveResource("public-area.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        loadValues();

        MessageUtil.consolePrint("已加载公共区域权限与设置配置");
    }

    private void loadValues() {
        enabled = config.getBoolean("enabled", true);

        permissionDefaults.clear();
        for (IslandPermission permission : IslandPermission.values()) {
            String key = "permissions." + permission.name();
            boolean value = config.getBoolean(key, false);
            permissionDefaults.put(permission, value);
        }

        settingDefaults.clear();
        for (IslandSetting setting : IslandSetting.values()) {
            String key = "settings." + setting.name();
            boolean value = config.getBoolean(key, true);
            settingDefaults.put(setting, value);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean getPermission(IslandPermission permission) {
        return permissionDefaults.getOrDefault(permission, false);
    }

    public boolean getSetting(IslandSetting setting) {
        return settingDefaults.getOrDefault(setting, true);
    }

    public void reload() {
        loadConfig();
    }
}
```

---

### Task 3: 修改 BasePermissionManager 添加公共区域回退

**Files:**
- Modify: `src/main/java/team/starm/starmskyblock/permission/BasePermissionManager.java`

- [ ] **Step 1: 在类级别添加 PublicAreaConfigManager 引用**

在 `configManager` 字段后添加：

```java
/** 公共区域配置管理器 */
protected final PublicAreaConfigManager publicAreaConfig;
```

- [ ] **Step 2: 修改构造方法**

```java
public BasePermissionManager(IslandManager islandManager, ConfigManager configManager,
                              PublicAreaConfigManager publicAreaConfig) {
    this.islandManager = islandManager;
    this.configManager = configManager;
    this.publicAreaConfig = publicAreaConfig;
}
```

- [ ] **Step 3: 修改 checkPermission() 方法**

将原有的 `checkPermission` 方法中的公共区域处理从硬编码 `return false` 改为使用 `publicAreaConfig`：

```java
public boolean checkPermission(Location location, UUID uuid, IslandPermission permission) {
    if (!StarMSkyblock.getInstance().getWorldManager().isSkyblockWorldName(location.getWorld().getName())) {
        return true;
    }

    Optional<Island> optIsland = getPlayerCurrentIslandMaxRange(islandManager, location);

    lastCheckWasAreaLocked = false;
    lastCheckWasPublicArea = false;

    if (optIsland.isEmpty()) {
        lastCheckWasPublicArea = true;
        // 公共区域：启用保护时检查权限配置，禁用保护时放行
        return !publicAreaConfig.isEnabled() || publicAreaConfig.getPermission(permission);
    }

    Island island = optIsland.get();
    int chunkX = location.getChunk().getX();
    int chunkZ = location.getChunk().getZ();

    if (!island.isChunkWithinIsland(chunkX, chunkZ)) {
        lastCheckWasAreaLocked = true;
        lastAreaLockedIsland = island;
        return false;
    }

    return hasPermission(island, uuid, permission);
}
```

唯一的改动在第 96 行：`return false;` → `return !publicAreaConfig.isEnabled() || publicAreaConfig.getPermission(permission);`

---

### Task 4: 修改 BaseSettingManager 添加公共区域回退

**Files:**
- Modify: `src/main/java/team/starm/starmskyblock/setting/BaseSettingManager.java`

- [ ] **Step 1: 添加构造方法参数**

添加字段和构造方法参数：

```java
/** 公共区域配置管理器 */
protected final PublicAreaConfigManager publicAreaConfig;

public BaseSettingManager(IslandManager islandManager, ConfigManager configManager,
                          PublicAreaConfigManager publicAreaConfig) {
    this.islandManager = islandManager;
    this.configManager = configManager;
    this.publicAreaConfig = publicAreaConfig;
}
```

- [ ] **Step 2: 修改 checkSetting() 方法**

```java
public boolean checkSetting(Location location, IslandSetting setting) {
    if (!StarMSkyblock.getInstance().getWorldManager().isSkyblockWorldName(location.getWorld().getName())) {
        return true;
    }

    Island island = getIslandAt(location);
    if (island == null) {
        // 公共区域：启用保护时检查设置配置，禁用保护时放行
        return !publicAreaConfig.isEnabled() || publicAreaConfig.getSetting(setting);
    }
    return island.getSetting(setting);
}
```

唯一的改动在第 55 行：`return true;` → `return !publicAreaConfig.isEnabled() || publicAreaConfig.getSetting(setting);`

---

### Task 5: 修改所有 BasePermissionManager 和 BaseSettingManager 的子类

**Files:**
- Modify: 所有继承 BasePermissionManager 的 12 个子类
- Modify: 所有继承 BaseSettingManager 的 6 个子类

这些子类都需要传递新的 `publicAreaConfig` 参数给父类构造方法。

- [ ] **Step 1: 查找所有 BasePermissionManager 子类的构造方法**

需要修改的文件：
- `permission/management/ManagementPermissionManager.java`
- `permission/droppickup/DropPickupPermissionManager.java`
- `permission/build/BuildPermissionManager.java`
- `permission/workblock/WorkblockPermissionManager.java`
- `permission/container/ContainerPermissionManager.java`
- `permission/redstone/RedstonePermissionManager.java`
- `permission/door/DoorPermissionManager.java`
- `permission/vehicle/VehiclePermissionManager.java`
- `permission/tool/ToolPermissionManager.java`
- `permission/item/ItemPermissionManager.java`
- `permission/entity/EntityPermissionManager.java`
- `permission/other/OtherPermissionManager.java`

每个子类的构造方法目前接受 `(IslandManager, ConfigManager)`，需要改为 `(IslandManager, ConfigManager, PublicAreaConfigManager)`，并将 `publicAreaConfig` 传递给 `super()`。

修改前示例：
```java
public BuildPermissionManager(IslandManager islandManager, ConfigManager configManager) {
    super(islandManager, configManager);
}
```

修改后：
```java
public BuildPermissionManager(IslandManager islandManager, ConfigManager configManager,
                              PublicAreaConfigManager publicAreaConfig) {
    super(islandManager, configManager, publicAreaConfig);
}
```

- [ ] **Step 2: 查找所有 BaseSettingManager 子类的构造方法**

需要修改的文件：
- `setting/PvpSettingManager.java`
- `setting/SpawnSettingManager.java`
- `setting/FireSpreadSettingManager.java`
- `setting/GriefSettingManager.java`
- `setting/ExplosionSettingManager.java`
- `setting/PhantomSpawnSettingManager.java`

相同的修改模式：添加 `PublicAreaConfigManager` 参数并传递给 `super()`。

---

### Task 6: 修改 IslandPermissionManager 传递公共区域配置

**Files:**
- Modify: `src/main/java/team/starm/starmskyblock/permission/IslandPermissionManager.java`

- [ ] **Step 1: 读取并修改构造方法传递 publicAreaConfig**

```java
public IslandPermissionManager(IslandManager islandManager, ConfigManager configManager,
                               StarMSkyblock plugin) {
    this.publicAreaConfig = plugin.getPublicAreaConfigManager(); // 添加
    // ... existing code
}
```

然后将 `publicAreaConfig` 传递给每个子管理器的构造方法。

---

### Task 7: 修改 IslandSettingManager 传递公共区域配置

**Files:**
- Modify: `src/main/java/team/starm/starmskyblock/setting/IslandSettingManager.java`

- [ ] **Step 1: 读取并修改构造方法传递 publicAreaConfig**

与 IslandPermissionManager 相同的模式，获取 `publicAreaConfig` 并传递给每个子设置管理器。

---

### Task 8: 在 StarMSkyblock.java 中集成

**Files:**
- Modify: `src/main/java/team/starm/starmskyblock/StarMSkyblock.java`

- [ ] **Step 1: 添加字段**

```java
// ========== 公共区域 ==========
private PublicAreaConfigManager publicAreaConfigManager;
```

- [ ] **Step 2: 在 initConfigs() 中初始化**

```java
private void initConfigs() {
    configManager = new ConfigManager(this);
    configManager.initialize();

    permissionConfigManager = new PermissionConfigManager(this);
    permissionConfigManager.initialize();

    settingsConfigManager = new SettingsConfigManager(this);
    settingsConfigManager.initialize();

    signConfigManager = new SignConfigManager(this);
    signConfigManager.initialize();

    generatorConfigManager = new GeneratorConfigManager(this);
    generatorConfigManager.initialize();

    upgradeConfigManager = new UpgradeConfigManager(this);
    upgradeConfigManager.initialize();

    // 公共区域配置
    publicAreaConfigManager = new PublicAreaConfigManager(this);
    publicAreaConfigManager.initialize();
}
```

- [ ] **Step 3: 在 registerListeners() 中注册 IslandBoundaryListener**

```java
private void registerListeners() {
    borderListener = new BorderListener(islandManager, worldManager, playerRepo);
    getServer().getPluginManager().registerEvents(borderListener, this);
    // ... existing listeners ...

    // 公共区域边界保护（启用时注册）
    if (publicAreaConfigManager.isEnabled()) {
        getServer().getPluginManager().registerEvents(
                new IslandBoundaryListener(islandManager, worldManager, configManager), this);
    }

    // ... rest of listeners ...
}
```

- [ ] **Step 4: 添加 getter 方法**

```java
public PublicAreaConfigManager getPublicAreaConfigManager() {
    return publicAreaConfigManager;
}
```

---

### Task 9: 构建验证

**Files:**
- N/A (验证步骤)

- [ ] **Step 1: 运行构建**

```bash
cd /Users/starm/Development/Minecraft/StarMSkyblock_1.0.0 && ./gradlew build
```

期望输出：`BUILD SUCCESSFUL`。

---

## Verification

1. **构建测试**：运行 `./gradlew build`，确认编译无错误
2. **配置文件生成**：确保 `public-area.yml` 被正确打包进 JAR，首次启动时会自动释放到插件数据目录
3. **逻辑验证**：
   - 在 `public-area.yml` 中设置某个权限为 `false` → 在公共区域中该行为被阻止
   - 设置 `enabled: false` → 公共区域不再受保护（放行所有行为）
   - 岛屿内部行为不受影响（仍使用岛屿自己的权限/设置）
   - 岛屿锁定区域行为不受影响（仍返回 `false` 并提示区域未解锁）
4. **`/isadmin reload` 后续可添加**：如需重载支持，在 `ReloadCommand.java` 中添加 `plugin.getPublicAreaConfigManager().reload();`