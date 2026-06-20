# P3 plugin.yml + onDisable + TrMenu reload 收尾清理

**日期**：2026-06-20
**范围**：todo.md 中 #32、#33、#34、#36、#37（#22 本轮不动，#35 标记为不再适用）

---

## 背景

todo.md P0–P2 已全部完成，剩 P3 的 6 项收尾清理。本 spec 处理 5 项：

- #32 — `plugin.yml` 中 WorldEdit 应为 `depend` 还是 `softdepend`
- #33 — 缺 FastAsyncWorldEdit / Multiverse-Core 软依赖
- #34 — `loadbefore: [Multiverse-Core]` 与运行时检测矛盾
- #36 — `onDisable` 未注销匿名 `PlayerJoinEvent` 监听器
- #37 — TrMenu hook 无 reload 安全

#22（`Bukkit.getOfflinePlayer(name)` 阻塞主线程）涉及 11 处调用点和 PlayerRepository 缓存改造，单独成 spec，本轮不动。

#35（Java 25 / Spigot 26.1.2 工具链）经用户确认是项目有意选择，标记为「不再适用」。

---

## 1. plugin.yml 重构（#32 + #33 + #34）

### 关键决策

todo #32 建议 `depend: [WorldEdit]`，但 CLAUDE.md 明确：`SchematicManager` 用反射同时支持 FAWE 和 WorldEdit。纯 FAWE 服务器（FAWE 不再注册 `WorldEdit` 插件名）会被 `depend: [WorldEdit]` 拒绝加载。

**方案**：保留 `WorldEdit` 在 `softdepend`，但在 `softdepend` 列表前部加入 `FastAsyncWorldEdit` 让 FAWE 优先于本插件加载，并删除 `loadbefore: [Multiverse-Core]`。

`softdepend` 中「存在则先于本插件加载」的语义 + `onEnable` 已有的 WorldEdit/FAWE 缺失检测（`StarMSkyblock.java:119`），等价于硬依赖但兼容 FAWE-only 部署。

### 改动

`src/main/resources/plugin.yml`：

```yaml
# 旧
load: POSTWORLD
loadbefore: [ Multiverse-Core ]
softdepend: [ PlaceholderAPI, WorldEdit, TrMenu, Vault, AuraSkills ]

# 新
load: POSTWORLD
softdepend: [ FastAsyncWorldEdit, WorldEdit, Multiverse-Core, PlaceholderAPI, TrMenu, Vault, AuraSkills ]
```

- 删除 `loadbefore: [Multiverse-Core]` —— 修复 #34：原配置让 Multiverse 在我们之后加载，导致 `SkyblockWorldManager.java:107` 的 `isPluginEnabled("Multiverse-Core")` 恒为 false，`mv import` 分支永不执行
- 加 `FastAsyncWorldEdit` —— 修复 #33 一部分
- 加 `Multiverse-Core` —— 修复 #33 另一部分 + 让 MV 优先加载
- `PlaceholderAPI/TrMenu/Vault/AuraSkills` 保留（#33 已声明）

---

## 2. onDisable 监听器注销 + 任务取消（#36）

### 当前状态

`StarMSkyblock.java:361-372`：

```java
public void onDisable() {
    if (taskManager != null) taskManager.saveAll();
    Bukkit.getScheduler().cancelTasks(this);
    if (sqliteManager != null) sqliteManager.close();
    MessageUtil.consolePrint("&c插件已关闭。");
}
```

### 问题

- 匿名 `PlayerJoinEvent` 监听器（`StarMSkyblock.java:262-270`）通过 `registerEvents` 直接注册，未持有引用，`/reload` 后旧监听仍挂在 Bukkit
- 其他具名监听器（`BorderListener`、`PortalListener` 等）虽是字段持有，但 `onDisable` 也没调 `HandlerList`

### 改动

把匿名 `PlayerJoinEvent` 监听器提为具名字段 `skullRefreshListener`，`onDisable` 调用 `HandlerList.unregisterAll(this)` 统一注销本插件注册的所有 `Listener`（包括 `IslandPermissionManager` / `IslandSettingManager` 等通过组合模式注册的内部监听器）。

```java
private Listener skullRefreshListener;  // 字段

// 在 registerIntegrations() 中：
skullRefreshListener = new Listener() { ... };
getServer().getPluginManager().registerEvents(skullRefreshListener, this);

// onDisable() 中：
public void onDisable() {
    if (taskManager != null) taskManager.saveAll();
    HandlerList.unregisterAll(this);   // 新增：注销本插件所有 Listener
    Bukkit.getScheduler().cancelTasks(this);
    if (sqliteManager != null) sqliteManager.close();
    MessageUtil.consolePrint("&c插件已关闭。");
}
```

### 关键点

`HandlerList.unregisterAll(Plugin)` 重载接受 `Plugin` 参数，精准注销我们注册的所有监听器（含权限/设置组合的内部监听器），不影响其他插件。Bukkit 自 1.x 起的稳定 API，无版本风险。

---

## 3. TrMenu hook reload 安全（#37）

### 当前状态

`StarMSkyblock.java:272-280`：

```java
if (getServer().getPluginManager().getPlugin("TrMenu") != null) {
    JavaScriptAgent.INSTANCE.putBinding("StarMSkyblockAPI", new StarMSkyblockHook());
    MessageUtil.consolePrint("已注册 TrMenu JS 物品源桥接");
    File menuDir = new File("plugins/TrMenu/menus/skyblockmenu");
    if (!menuDir.exists()) extractSkyblockMenu();
}
```

### 问题

- 若 TrMenu 在本插件之后加载（如管理员临时 `/plugman load TrMenu`），`putBinding` 永不执行 —— TrMenu 菜单里 `StarMSkyblockAPI.xxx` 调用全是 `null`
- 反射 / `NoClassDefFoundError` 风险：TrMenu 被卸载但本插件仍在运行时，`JavaScriptAgent.INSTANCE` 这一行可能抛 `NoClassDefFoundError`。当前没有 catch，会冒泡到 `onEnable` 顶层
- todo 提「未判 `INSTANCE` 非 null」—— 反编译验证后 `INSTANCE` 是 Kotlin object 静态字段，正常情况一定非 null，但 TrMenu 缺类时会变 `NoClassDefFoundError`

### 改动

抽 `hookTrMenu()` 方法，调用点改为两处：

1. `onEnable` 的 `registerIntegrations()`：TrMenu 已加载时立即调一次
2. `PluginEnableEvent` 监听器：TrMenu 后加载时再调一次（监听器被第 2 节的 `HandlerList.unregisterAll(this)` 覆盖，无需额外注销）

```java
private void hookTrMenu() {
    try {
        JavaScriptAgent.INSTANCE.putBinding("StarMSkyblockAPI", new StarMSkyblockHook());
        MessageUtil.consolePrint("已注册 TrMenu JS 物品源桥接");
        File menuDir = new File("plugins/TrMenu/menus/skyblockmenu");
        if (!menuDir.exists()) extractSkyblockMenu();
    } catch (Throwable t) {
        // NoClassDefFoundError / LinkageError 都兜住
        MessageUtil.consoleError("TrMenu hook 失败: " + t.getMessage(), t);
    }
}

// registerIntegrations() 中：
if (getServer().getPluginManager().getPlugin("TrMenu") != null) {
    hookTrMenu();
}

// 新增 PluginEnableEvent 监听器（具名字段，注册到 Bukkit）：
trMenuLateLoadListener = new Listener() {
    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if ("TrMenu".equals(event.getPlugin().getName())) {
            hookTrMenu();
        }
    }
};
getServer().getPluginManager().registerEvents(trMenuLateLoadListener, this);
```

### 幂等性

- `putBinding` 走 `Map.put`，重复调用幂等，不重复注册导致冲突
- `extractSkyblockMenu` 内部已有 `if (!target.toFile().exists())` 判空，重复调用安全

### 时机说明

`PluginEnableEvent` 触发时机是 TrMenu 的 `onLoad` 之后、`onEnable` 期间。`JavaScriptAgent.INSTANCE` 此时已初始化（TrMenu 的 `onLoad` 阶段初始化静态字段），所以 `PluginEnableEvent` 触发后立即调 `putBinding` 安全。

---

## 4. 验证方式

P3 改动以「配置 + 生命周期」为主，没有可单测的逻辑。验证手段：

1. **plugin.yml 生效** — 服务器启动后查 `/plugins` 列表确认加载顺序：FAWE/WorldEdit/Multiverse-Core 应在 StarMSkyblock 之前；`/mv list` 应能看到三个空岛世界被 Multiverse 导入（修复 #34 的关键证据）
2. **onDisable 监听器注销** — `/reload confirm` 后查 Bukkit 监听器数（可用 `/event listeners` 或临时加一行 `HandlerList.getRegisteredListeners(plugin).size()` 日志），确认 reload 前后数量一致、不翻倍
3. **TrMenu 后加载场景** — 手动用 `/plugman load TrMenu` 在本插件之后加载 TrMenu，确认 console 出现「已注册 TrMenu JS 物品源桥接」且菜单里 `StarMSkyblockAPI.xxx` 可调用；再 `/plugman unload TrMenu` 后确认本插件没崩（catch `Throwable` 兜底生效）
4. **回归** — 跑一遍 `/is create` / `/is delete` / `/is task submit` / `/is upgrade` / PAPI 占位符 / Multiverse `mv import`，确认无新异常

---

## 5. todo.md 状态更新

实施完成后更新 `todo.md`：

- **#32** → `[x]`，补说明：保留 `WorldEdit` 在 `softdepend`（不是 `depend`），原因是 `SchematicManager` 反射同时支持 FAWE，FAWE-only 服务器不一定注册 `WorldEdit` 插件名，`depend: [WorldEdit]` 会拒绝加载
- **#33** → `[x]`：补 `FastAsyncWorldEdit` 和 `Multiverse-Core` 到 `softdepend`
- **#34** → `[x]`：删除 `loadbefore: [Multiverse-Core]`
- **#35** → `[x]` 标注「不再适用」：保留 Java 25 / Spigot 26.1.2，用户确认是项目有意选择
- **#36** → `[x]`：匿名监听器提为具名字段，`onDisable` 调 `HandlerList.unregisterAll(this)`
- **#37** → `[x]`：抽 `hookTrMenu()` 方法，加 `PluginEnableEvent` 监听器处理 TrMenu 后加载，`try/catch Throwable` 兜 `NoClassDefFoundError`
- **#22** 保持 `[ ]`：本轮不动，下一轮处理

---

## 实施顺序

1. 改 `plugin.yml`（#32/#33/#34）
2. 改 `StarMSkyblock.java`：匿名监听器提字段 + `onDisable` 加 `HandlerList.unregisterAll(this)`（#36）
3. 改 `StarMSkyblock.java`：抽 `hookTrMenu()` + `PluginEnableEvent` 监听器 + `try/catch`（#37）
4. 更新 `todo.md` 状态
5. `./gradlew build` 验证编译通过
