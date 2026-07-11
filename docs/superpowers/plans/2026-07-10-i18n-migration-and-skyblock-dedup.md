# 实施计划：OPTIMIZATION.md #23（i18n 迁移收尾）+ #24（SkyblockExpansion 去重）

> 日期：2026-07-10
> 范围：把 #23 列出的 7 个文件中约 75 处硬编码中文迁移到消息键；同时对 SkyblockExpansion 做 #24 的查找回退模式去重。两者在 SkyblockExpansion 上重叠，合并为一次改写。

## 核心决策（已通过代码核查确认）

1. **`IslandPermissionLevel.getDisplayName()`** → 改为 `MessageUtil.format("role." + name().toLowerCase())`。`fromString()` **不动**——它内部已调用 `getDisplayName().equalsIgnoreCase(roleName)` 做中文反查，i18n 后中文值仍来自 zh_CN.yml，反查行为字节级不变。因此 **DB role 列存储格式无论是什么都不受影响**。`permissions.yml` 实际用枚举名（OWNER/ADMIN/…），config.yml/upgrades.yml 不含 role 名。
2. **`OreDisplayName`** 仅被 SkyblockExpansion 3 处 PAPI 调用消费（不涉等级系统/命令）。保留枚举作为"可翻译材质注册表"，`toChinese()` 对已知材质走 `MessageUtil.format("material.<name>")`，未知材质原样返回（用枚举集合判定，避免 missing-key 警告）。
3. **PAPI 返回值** 用 `MessageUtil.format(key, args)`（返回已格式化字符串，不发送）——正是其设计用途。
4. **策略**：保留每处当前的精确措辞与颜色，用专用键承载，不做近义消息合并（合并是独立重构）。`IslandCreateTask`/`IslandDeleteTask` 迁移后改走 `MessageUtil.send`，顺带修复"丢失 `-s` 静默标志"问题（#23 明确指出的问题）。
5. **范围外**：TrMenu 菜单 lore（`skyblockmenu/Permissions/*.yml` 里硬编码 `&a岛主`）属另一条迁移路径且受 #19 版本提取问题阻塞，本轮不动；`SkyblockExpansion` 中大量 `&f-` 占位符非 #23 列项，保留不动以降低改动面。

## 新增消息键（zh_CN.yml，分 5 组）

```yaml
role:
  owner: "岛主"
  admin: "管理员"
  mod: "风纪委员"
  member: "岛员"
  coop: "合作者"
  visitor: "访客"
dimension:
  normal: "主世界"
  nether: "下界"
  end: "末地"
material:
  cobblestone: "圆石"
  stone: "石头"
  coal_ore: "煤矿石"
  copper_ore: "铜矿石"
  lapis_ore: "青金石矿石"
  iron_ore: "铁矿石"
  gold_ore: "金矿石"
  redstone_ore: "红石矿石"
  diamond_ore: "钻石矿石"
  emerald_ore: "绿宝石矿石"
  basalt: "玄武岩"
  netherrack: "下界岩"
  nether_quartz_ore: "下界石英矿"
  nether_gold_ore: "下界金矿石"
  gilded_blackstone: "镶金黑石"
  ancient_debris: "远古残骸"
  glowstone: "荧石"
  soul_soil: "灵魂土"
  end_stone: "末地石"
  sand: "沙子"
placeholder:
  public-area: "公共区域"
  max-level-reached: "&f已达到最高等级"
  island-id: "岛屿 #{id}"
  yes: "&a是"
  no: "&c否"
```

`island.create.*` 新增：`in-progress`、`allocating`、`preparing-world`、`generating-normal`、`generating-nether`、`generating-end`、`unexpected-error`、`success-header`、`success-time{time}`、`success-id{id}`、`success-position{x},{y},{z}`、`partial-success`、`divider-error`（红色分隔线）、`teleported`、`admin-broadcast{player}{id}{time}`。

`island.delete.*` 新增：`task-error`、`task-ejected`、`task-success{current}{max}`、`task-data-error`、`admin-broadcast{player}`。

`island.portal.*` 新增：`nether-not-unlocked`、`first-join-nether`、`bounds-locked`、`bounds-out`、`entity-portal-blocked{x}{y}{z}{reason}`、`reason-locked`、`reason-out`。

`level` 新增：`blocks-over-limit-header: "  超出阈值的方块: "`。

## 逐文件改动

### 1. `IslandPermissionLevel.java`
- 删除 `displayName` 字段及构造参数（保留 `permissionLevel`、`color`）。
- `getDisplayName()` → `return MessageUtil.format("role." + name().toLowerCase(Locale.ROOT));`
- `fromString()` 保持不变（已通过 `getDisplayName()` 反查）。
- 新增 `import team.starm.starmskyblock.message.MessageUtil;` 和 `java.util.Locale`。

### 2. `OreDisplayName.java`
- 枚举常量去掉 `chineseName` 参数；保留 20 个常量作为已知集合。
- `MATERIAL_TO_CHINESE` map → `KNOWN` Set（枚举名集合）。
- `toChinese(materialName)`：null→null；未知→原名；已知→`MessageUtil.format("material." + materialName.toLowerCase(Locale.ROOT))`。
- 新增 `import MessageUtil, Locale, HashSet, Set`。

### 3. `LevelManager.java`（L217-230）
- `Component.text("  超出阈值的方块: ", NamedTextColor.GREEN)` → `Component.text(MessageUtil.format("level.blocks-over-limit-header"), NamedTextColor.GREEN)`。该串无颜色码，GREEN 干净套用。

### 4. `IslandCreateTask.java`
- 删除私有 `sendMessage(String)` helper（L223-228，绕过了静默标志）。
- 所有 `sendMessage("§...")` → `MessageUtil.send(player, "island.create.<key>", Map.of(...))`。
- L273-279 管理员广播 → `MessageUtil.send(onlinePlayer, "island.create.admin-broadcast", Map.of(...))`。
- 阶段标签 "主世界"/"下界"/"末地"（L106/115/124 传入 `pasteSchematicSync` 用于 `consoleError` 日志）→ 用 `MessageUtil.format("dimension.*")`，保持日志可读。
- 注意：`MessageUtil.send` 在玩家离线时本身安全（内部判 `instanceof Player`+silent），但需先 `Bukkit.getPlayer(uuid)` 拿 player；保留在线判断以避免无谓调用。

### 5. `IslandDeleteTask.java`
- L116/L199（重复）→ `island.delete.task-error`；L147 → `island.delete.task-ejected`；L176 → `island.delete.task-success`（`{current}=deleteCount+1`，`{max}`）；L181 → `island.delete.admin-broadcast`；L187 → `island.delete.task-data-error`。
- 这些消息在主线程 `BukkitRunnable` 内发送，直接 `MessageUtil.send(player, key, args)`。

### 6. `PortalListener.java`
- L156 三元 → `island.portal.reason-locked`/`reason-out`；L157 msg → `MessageUtil.format("island.portal.entity-portal-blocked", Map.of("x","y","z","reason"))` 后 `MessageUtil.sendMessage(p, Component)` 或 `send`（成员/owner 各发一次）。
- L196 → `island.portal.nether-not-unlocked`；L231 → `island.portal.first-join-nether`。
- L308/L310（`checkIslandBounds` 返回值）→ `island.portal.bounds-locked`/`bounds-out`（返回格式化串，调用方 `player.sendMessage` 改 `MessageUtil.send`）。

### 7. `SkyblockExpansion.java`（#24 去重 + #23 迁移合并）

**新增 4 个 helper**：
```java
private Optional<Island> findIslandAt(int chunkX, int chunkZ) {
    IslandManager im = plugin.getIslandManager();
    Optional<Island> opt = im.getIslandAt(chunkX, chunkZ);
    if (opt.isEmpty()) opt = im.getIslandAtMaxRange(chunkX, chunkZ);
    return opt;
}
private <T> T withIslandAt(int chunkX, int chunkZ, T fallback, Function<Island,T> extractor) {
    try { return findIslandAt(chunkX, chunkZ).map(extractor).orElse(fallback); }
    catch (RuntimeException e) { MessageUtil.consoleWarn("SkyblockExpansion placeholder 渲染失败: " + e.getMessage()); return fallback; }
}
private <T> T withOwnIsland(UUID uuid, T fallback, Function<Island,T> extractor) { /* 同上，用 getIslandByPlayer */ }
private String formatRole(Island island, UUID uuid) {
    IslandPermissionLevel role = island.getMemberRole(uuid);
    if (role != IslandPermissionLevel.VISITOR) return role.getColor() + role.getDisplayName();
    if (island.isCoop(uuid)) return IslandPermissionLevel.COOP.getColor() + IslandPermissionLevel.COOP.getDisplayName();
    return IslandPermissionLevel.VISITOR.getColor() + IslandPermissionLevel.VISITOR.getDisplayName();
}
```

**7 个方法塌缩**（`getIslandName`/`getPlayerRole`/`getIslandLevelHere`/`getIslandValueHere`/`getGeneratorLevelHere`/`getPlayerOwnIslandName`/`getPlayerOwnRole`）：每个缩到 3-6 行，用 `withIslandAt`/`withOwnIsland`/`formatRole` + `MessageUtil.format` 取默认值（`placeholder.public-area`、`placeholder.island-id`、访客 role 显示）。`getPlayerRole`/`getPlayerOwnRole` 共用 `formatRole`，回退值均为访客显示。

**#23 硬编码中文迁移**（仅 #23 列项）：
- `"公共区域"`（L57/424/441）→ `MessageUtil.format("placeholder.public-area")`。
- `dimension` placeholder switch（L101-106）→ `dimension.normal/nether/end`。
- `appendDimensionRates` 调用参数（L650-654）→ `MessageUtil.format("dimension.*")`。
- `"&f已达到最高等级"`（L155/164/173/269）→ `placeholder.max-level-reached`。
- `"岛屿 #" + id`（L432/598）→ `MessageUtil.format("placeholder.island-id", Map.of("id", id))`。
- `"&a是"/"&c否"`（L750/778）→ `placeholder.yes`/`placeholder.no`。
- role 显示经 `IslandPermissionLevel.getDisplayName()` 迁移自动解决（无需在此改）。
- `&f-` 占位符保留不动。

## 实施顺序
1. zh_CN.yml 追加 5 组键。
2. `IslandPermissionLevel`、`OreDisplayName`、`LevelManager`(L219) —— 三个基础迁移（其他文件依赖）。
3. `IslandCreateTask`、`IslandDeleteTask`、`PortalListener`。
4. `SkyblockExpansion`（去重 + 迁移合并）。
5. `./gradlew build` 编译验证；grep 复查 7 文件无残留硬编码中文（排除注释/日志 stage label 已迁移）。

## 风险与回滚
- **`getDisplayName()` 行为不变**：zh_CN 值即原硬编码中文，fromString 反查保持。唯一新增依赖是 `MessageUtil.format` 的 map 查找（仅展示路径调用，非每 tick 热路径）。
- **静默标志**：Create/DeleteTask 改走 `MessageUtil.send` 后会响应 `-s`——这是 #23 预期修复，非回归。
- **回滚**：全部为 Java + zh_CN.yml 改动，单次提交可整体 revert；不触碰 DB 代码（符合 DB 冻结约束）。
