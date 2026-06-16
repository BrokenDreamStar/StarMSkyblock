# Public Worlds 功能实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标:** 支持在 config.yml 中配置指定世界（如 world）为公共区域，使插件在这些世界中应用 public-area.yml 的权限和设置规则

**架构:** 在 ConfigManager 中加载 public-worlds 列表，SkyblockWorldManager 提供 isPublicWorld() 判断方法，然后在 BaseSettingManager.checkSetting() 和 BasePermissionManager.checkPermission() 的快速路径中，对公共世界返回 publicAreaConfig 的值而非直接 return true

**技术栈:** Java 25, Spigot/Paper API, Bukkit

---

### Task 1: ConfigManager — 加载 public-worlds 配置

**文件:**
- Modify: `src/main/java/team/starm/starmskyblock/config/ConfigManager.java`
- Modify: `src/main/resources/config.yml`

**config.yml 改动:** 在文件末尾 `set-respawn-on-join` 之后新增:

```yaml
# 公共世界列表
# 这些世界会被视为空岛世界中的"公共区域"，
# 使用 public-area.yml 中的权限和设置规则
# 可配置多个世界，如：
#   - "world"
#   - "resource"
public-worlds: []
```

**ConfigManager.java 改动:**
- [ ] **Step 1:** 新增 import `java.util.Collections` 和 `java.util.HashSet` 和 `java.util.Set`

- [ ] **Step 2:** 在 `maxDeleteTimes` 字段附近新增字段:

```java
private volatile Set<String> publicWorlds = Collections.emptySet();
```

- [ ] **Step 3:** 在 `loadConfig()` 方法的 `this.maxDeleteTimes = config.getInt("max-delete-times", 3);` 之后加载:

```java
this.publicWorlds = new HashSet<>(config.getStringList("public-worlds"));
```

- [ ] **Step 4:** 新增 Getter 方法，放在 `isSetRespawnOnJoin()` 之后:

```java
public boolean isPublicWorld(String worldName) {
    return publicWorlds.contains(worldName);
}
```

- [ ] **Step 5:** 检查是否有导入问题 — `HashSet` 需要 `java.util.HashSet`，`Set` 需要 `java.util.Set`，`Collections` 需要 `java.util.Collections`

---

### Task 2: SkyblockWorldManager — 新增 isPublicWorld 方法

**文件:**
- Modify: `src/main/java/team/starm/starmskyblock/world/SkyblockWorldManager.java`

- [ ] **Step 1:** 在 `isEndWorld()` 方法之后新增:

```java
/**
 * 判断世界名称是否为配置的公共世界
 */
public boolean isPublicWorld(String worldName) {
    return configManager.isPublicWorld(worldName);
}
```

---

### Task 3: BaseSettingManager — 修改快速路径

**文件:**
- Modify: `src/main/java/team/starm/starmskyblock/setting/BaseSettingManager.java`

- [ ] **Step 1:** 替换 `checkSetting()` 方法开头的快速路径（第 59-61 行）:

```java
// 当前:
if (!StarMSkyblock.getInstance().getWorldManager().isSkyblockWorldName(location.getWorld().getName())) {
    return true;
}

// 改为:
String worldName = location.getWorld().getName();
if (!StarMSkyblock.getInstance().getWorldManager().isSkyblockWorldName(worldName)) {
    if (StarMSkyblock.getInstance().getWorldManager().isPublicWorld(worldName)) {
        return !publicAreaConfig.isEnabled() || publicAreaConfig.getSetting(setting);
    }
    return true;
}
```

---

### Task 4: BasePermissionManager — 修改快速路径

**文件:**
- Modify: `src/main/java/team/starm/starmskyblock/permission/BasePermissionManager.java`

- [ ] **Step 1:** 替换 `checkPermission()` 方法开头的快速路径（第 95-97 行）:

```java
// 当前:
if (!StarMSkyblock.getInstance().getWorldManager().isSkyblockWorldName(location.getWorld().getName())) {
    return true;
}

// 改为:
String worldName = location.getWorld().getName();
if (!StarMSkyblock.getInstance().getWorldManager().isSkyblockWorldName(worldName)) {
    if (StarMSkyblock.getInstance().getWorldManager().isPublicWorld(worldName)) {
        lastCheckWasPublicArea = true;
        return !publicAreaConfig.isEnabled() || publicAreaConfig.getPermission(permission);
    }
    return true;
}
```

---

### Task 5: 验证构建

- [ ] **Step 1:** 运行构建:

```bash
cd /Users/starm/Development/Minecraft/StarMSkyblock_1.0.0 && ./gradlew build
```

预期: BUILD SUCCESSFUL。输出的 JAR 位于 `build/libs/StarMSkyblock.jar`。