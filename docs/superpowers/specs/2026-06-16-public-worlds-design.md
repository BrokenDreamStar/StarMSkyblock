---
name: Public Worlds Feature Design
description: 支持配置指定世界（如 world）为公共区域，复用 public-area.yml 规则进行权限与设置管理
---

# Public Worlds 功能设计文档

## 背景

当前 StarMSkyblock 插件仅在三个空岛世界（配置的 world-name、`_nether`、`_the_end`）中生效：
- 在空岛世界中，`BaseSettingManager` 和 `BasePermissionManager` 会逐段检查：岛屿区域 → 锁定区域 → 公共区域
- 在其他任何世界（如服务器默认生成的 `world`）中，所有权限和设置检查直接返回 `true`（放行一切）

需求：允许服主将指定世界配置为"公共区域"，使得插件在这些世界中也能应用 `public-area.yml` 中定义的权限和设置规则（如禁止破坏、禁用 PvP、防爆等）。

## 设计要点

### 配置文件

在 `config.yml` 新增：
```yaml
# 公共世界列表（支持多个）
# 这些世界会被视为空岛世界中的"公共区域"，
# 使用 public-area.yml 中的权限和设置规则
public-worlds:
  - "world"
```

所有列出的公共世界共享同一套规则（`public-area.yml`），不需要额外配置文件。

### 代码变更

#### 1. ConfigManager.java

- 新增字段：`private volatile Set<String> publicWorlds = Collections.emptySet();`
- 在 `loadConfig()` 中加载：`this.publicWorlds = new HashSet<>(config.getStringList("public-worlds"));`
- 新增 Getter：`public boolean isPublicWorld(String worldName)`

#### 2. SkyblockWorldManager.java

- 新增方法 `public boolean isPublicWorld(String worldName)`，委托给 `configManager.isPublicWorld()`
- 使其他类可通过 `worldManager.isPublicWorld(name)` 统一判断

#### 3. BaseSettingManager.checkSetting() — 修改快速路径

```java
// 当前（第 59-61 行）：
if (!worldManager.isSkyblockWorldName(worldName)) {
    return true;
}

// 改为：
if (!worldManager.isSkyblockWorldName(worldName)) {
    if (worldManager.isPublicWorld(worldName)) {
        return !publicAreaConfig.isEnabled() || publicAreaConfig.getSetting(setting);
    }
    return true;
}
```

#### 4. BasePermissionManager.checkPermission() — 修改快速路径

```java
// 当前（第 95-97 行）：
if (!worldManager.isSkyblockWorldName(worldName)) {
    return true;
}

// 改为：
if (!worldManager.isSkyblockWorldName(worldName)) {
    if (worldManager.isPublicWorld(worldName)) {
        lastCheckWasPublicArea = true;
        return !publicAreaConfig.isEnabled() || publicAreaConfig.getPermission(permission);
    }
    return true;
}
```

设置 `lastCheckWasPublicArea = true` 后，原拒绝消息系统（`sendDenyMessage()`）会自动显示"公共区域不允许进行操作！"，无需额外修改。

### 影响范围

所有 12 个权限管理器 + 6 个设置管理器均会受益，因为：
- 权限检查全部继承自 `BasePermissionManager.checkPermission()`
- 设置检查全部继承自 `BaseSettingManager.checkSetting()`

修改基类快速路径即可覆盖全部子类。

## 不变的设计决策

1. **不引入新配置文件** — 仅修改 `config.yml`，复用现有 `public-area.yml`
2. **不创建新 Java 类** — 仅在现有类中新增字段和方法
3. **不触及子类** — 所有 18 个子管理器无需任何修改
4. **不改动 `locked-area.yml` 逻辑** — 公共世界不存在岛屿概念，没有锁定区域
5. **向后兼容** — 未在 `public-worlds` 中配置的世界行为不受影响（仍 return true）

## 验证方式

1. 启动服务器，检查控制台无异常输出
2. 观察 `world` 主城世界，验证 `public-area.yml` 中的规则生效（如 PvP 禁用、禁止破坏等）
3. 观察空岛世界行为是否与修改前一致
4. 在未配置的第三方世界验证原有放行行为正常