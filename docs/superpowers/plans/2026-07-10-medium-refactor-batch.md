# MEDIUM 级优化批量实施计划（2026-07-10）

## 概述

执行 OPTIMIZATION.md 中 7 项 MEDIUM（🟡）建议。经 4 路并行代码核查后：

- **#16 经核查为误报，跳过**（详见下节）
- **#31 + #32 合并改造**（目标一致、改造点重叠，合并改一处签名即可）
- 其余 4 项（#26 / #27 / #28 / #33）独立实施

实际改动 **6 项**（#16 不改）。所有项均不触及被冻结的 DB 代码（SQLiteManager / Repository / LevelManager DB 写）。

---

## #16 reload 非原子屏障 —— 跳过（误报）

**核查结论**：

1. `/isadmin reload` 与所有权限/设置事件监听器同在 Bukkit **主线程串行**执行，reload 期间不会有事件处理器插入。step 8（publicArea 重载）与 step 9（lockedArea 重载）之间不可能插入一次权限检查。
2. `BasePermissionManager.check()` 的三条分支（publicArea / lockedArea / island 自身权限）**互斥**，单次检查只走其一，不会在同一次 check 中混合新旧值。
3. OPTIMIZATION.md 对数据流的描述有误：`permissionConfigManager` 只在 publicArea/lockedArea 的 `loadConfig()` 中被读以重新填充各自的 EnumMap 快照，**权限检查热路径本身不直接读 `permissionConfigManager`**，因此不存在"读到新 permissionConfig 却旧 publicArea"的混合。
4. 真实窗口（step 8→9 之间 publicArea 新 / lockedArea 旧）在主线程串行模型下**不可达**。

`volatile reloading` 屏障在当前单线程模型下是无意义的复杂化；仅当未来将权限检查移至异步线程时才有意义。**保持现状，不改动。** 若未来引入异步权限检查需重新评估。

---

## #31 + #32（合并）Base 类构造器注入 `plugin` + `worldManager`

**目标**：消除两个 Base 类中 8 处 `StarMSkyblock.getInstance().getWorldManager()` 热路径调用（#32），同时统一 DropPickup 的不一致构造器（#31）——两者都给 Base 类补注入依赖，改造点重叠，合并为一次签名变更。

### 改动

**Base 类构造器 4 参 → 6 参**：
- `permission/BasePermissionManager.java`：构造器加 `JavaPlugin plugin, SkyblockWorldManager worldManager`，新增两个 `protected final` 字段；4 处 `StarMSkyblock.getInstance().getWorldManager().isXxx()` 改用 `this.worldManager.isXxx()`。
- `setting/BaseSettingManager.java`：同上，4 处改用注入字段。

**18 个子类统一 6 参构造器**（机械改动，每个加 2 参并传 `super()`）：
- 12 个权限子类：`permission/manager/{Build,Container,Door,Entity,Item,Management,Other,Redstone,Tool,Vehicle,Workblock}PermissionManager.java` + DropPickup。
- 6 个设置子类：`setting/manager/{Explosion,FireSpread,Grief,PhantomSpawn,Pvp,Spawn}SettingManager.java`。

**DropPickup 去特殊化**：
- `permission/manager/DropPickupPermissionManager.java`：删除私有 `plugin` 字段和第 5 参，`runTask(plugin, ...)` 改用继承的 `this.plugin`。从此构造器与其他 11 个一致。

**ToolPermissionManager 统一获取方式**：
- `permission/manager/ToolPermissionManager.java:466`：`JavaPlugin.getProvidingPlugin(getClass())` 改用继承的 `this.plugin`，消除第三种 plugin 获取方式。

**两个协调器补传 worldManager**：
- `permission/IslandPermissionManager.java`：构造器已持有 `plugin`，新增接收 `SkyblockWorldManager worldManager`，传给 12 子类。
- `setting/IslandSettingManager.java`：同上，传给 6 子类。
- `StarMSkyblock.java`：`initPermissions()` / `registerListeners()` 处构造两个协调器时补传 `worldManager`（`worldManager` 在 `initWorlds()` 创建，早于这两步，时序可行）。

**收益**：热路径消除全局单例查找 + 8 处方法链；两个 Base 类可脱离 `getInstance()` 单测；DropPickup 构造器回归统一，可列表化注册。

---

## #28 grid-key 编码去重 + getIslandAt/getIslandAtMaxRange 统一

**核查修正**：实际 **6 处**（非 OPTIMIZATION.md 所述 4 处），`BlockBreakTaskListener.chunkKey` 用不同编码（3 字段 `0xFFFF`，含 worldIndex），**不纳入去重**。

### 改动

**新建工具类** `island/GridKeys.java`：
```java
public final class GridKeys {
    private GridKeys() {}
    /** 将两个 int 坐标编码为 long 网格键：(x<<32)|(z&0xffffffffL)。 */
    public static long encode(int x, int z) {
        return (((long) x) << 32) | (z & 0xffffffffL);
    }
}
```

**替换 6 处手写编码**：
- `island/IslandManager.java`：`addToGridIndex`(:608)、`removeFromGridIndex`(:617)、`getIslandAt`(:665)、`getIslandAtMaxRange`(:684) —— 改用 `GridKeys.encode(gx, gz)`。
- `island/IslandDeleteTask.java`：`:136`（`islandChunks.add`）、`:144-145`（玩家位置键）—— 改用 `GridKeys.encode(cx, cz)` / `GridKeys.encode(blockX>>4, blockZ>>4)`。

**合并 getIslandAt / getIslandAtMaxRange**（IslandManager.java）：
提取私有方法，以边界谓词参数化：
```java
private Optional<Island> findInGrid(int chunkX, int chunkZ, BiPredicate<Island, int[]> within) {
    int cellSize = gridManager.getGridCellSize();
    int gx = (int) Math.round((double) chunkX / cellSize);
    int gz = (int) Math.round((double) chunkZ / cellSize);
    Integer id = islandGridIndex.get(GridKeys.encode(gx, gz));
    if (id != null) {
        Island island = islandsById.get(id);
        if (island != null && within.test(island, new int[]{chunkX, chunkZ})) {
            return Optional.of(island);
        }
    }
    return Optional.empty();
}
```
`getIslandAt` → `findInGrid(x, z, (is, c) -> is.isChunkWithinIsland(c[0], c[1]))`；
`getIslandAtMaxRange` → `findInGrid(x, z, (is, c) -> is.isChunkWithinMaxRange(c[0], c[1]))`。
（或用 `boolean maxRange` 标志代替谓词，更简单；实现时择优。）

**不改动**：`task/listener/BlockBreakTaskListener.chunkKey`（不同编码、不同语义）。

---

## #27 TaskManager 奖励发放去重

**核查结论**：`claimReward`(:418-467) 与 `giveForceCompleteRewards`(:564-632) 的奖励块差异全部归结为同一根因——"玩家此刻是否在线"。helper 内部 `Bukkit.getPlayer(uuid) != null` 自检即可覆盖，**无需 `requireOnline` 参数**。

### 改动

`task/TaskManager.java` 新增私有 helper（只负责金币+物品+命令三块，自检在线态）：
```java
private void grantRewards(UUID uuid, TaskReward rewards) {
    Player player = Bukkit.getPlayer(uuid);
    boolean online = player != null;
    String playerName = online ? player.getName()
            : Optional.ofNullable(Bukkit.getOfflinePlayer(uuid).getName()).orElse(uuid.toString().substring(0, 8));

    if (rewards.money() > 0) {
        net.milkbowl.vault.economy.Economy econ = plugin.getEconomy();
        if (econ != null) {
            econ.depositPlayer(Bukkit.getOfflinePlayer(uuid), rewards.money()); // OfflinePlayer 重载对在线玩家同样工作
        }
    }
    if (online) {
        for (TaskReward.ItemReward ir : rewards.items()) {
            Material mat = Material.matchMaterial(ir.material());
            if (mat == null) continue;
            ItemStack item = new ItemStack(mat, ir.amount());
            Map<Integer, ItemStack> rem = player.getInventory().addItem(item);
            for (ItemStack drop : rem.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }
    for (String cmd : rewards.commands()) {
        if (cmd == null || cmd.isEmpty()) continue;
        String parsed = cmd.replace("%player_name%", playerName).replace("%player%", playerName);
        if (parsed.startsWith("server:")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed.substring("server:".length()).trim());
        } else if (parsed.startsWith("player:")) {
            if (online) player.performCommand(parsed.substring("player:".length()).trim());
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }
}
```

`claimReward` / `giveForceCompleteRewards` 各自删除这三块，改为调用 `grantRewards(uuid, rewards)`；**保留在各自方法中**：`prog` 状态变更（setClaimed/clear/markDirty/setNotified/savePlayerProgress/completedTaskIds）、消息 key（`task.completed-no-reward` / `task.reward-claimed` / `task.admin-force-complete*`）、返回值、空奖励早退逻辑。

**行为等价性**：对两调用方逐位等价（在线态自检覆盖了原 `Player` vs `OfflinePlayer` 重载差异、`if(online)` 守卫差异、名字源差异）。附带发现：`giveForceCompleteRewards` 的离线路径当前经 `SetTaskCommand.resolvePlayer` 不可达，但 helper 保留该能力以备未来调用。

---

## #33 PortalListener 坐标计算器去重

**核查结论**：`calculateNetherPortalLocation`(:271-281) 与 `calculateOverworldPortalLocation`(:287-297) 差异**仅缩放因子**（÷8 vs ×8），其余完全相同；`getIslandLocation`(:438-450) 与两者本质不同（固定出生点、Y/yaw/pitch 来自配置、不缩放 from 偏移），**不合并入 scalePortalLocation**，仅共享中心块计算。

### 改动

`listener/PortalListener.java`：

1. 新增 `getIslandCenterBlockXZ(Island)` 返回 `double`（中心块 = `centerChunkX * 16.0 + 8.0`），供两个传送门方法使用；`getIslandLocation` 的 int 版中心块计算也可调用（double 自动兼容 int，或提供重载）。
2. 新增 `scalePortalLocation(Location from, World targetWorld, Island island, double factor)` 合并两方法（factor：下界 `1.0/8.0`，主世界 `8.0`）：
```java
private Location scalePortalLocation(Location from, World targetWorld, Island island, double factor) {
    double[] center = getIslandCenterBlockXZ(island);
    double offsetX = from.getX() - center[0];
    double offsetZ = from.getZ() - center[1];
    return new Location(targetWorld, center[0] + offsetX * factor, from.getY(),
            center[1] + offsetZ * factor, from.getYaw(), from.getPitch());
}
```
3. 调用点（onEntityPortal 分支 / handleToNether / handleFromNether）改用 `scalePortalLocation(..., 1.0/8.0)` 或 `(..., 8.0)`。
4. 删除原两个方法。

---

## #26 getIslandByPlayerName 重写（消除 deprecated 阻塞调用）

**核查发现**：该方法**零内部调用者**（死代码），但保留为 public 方法供潜在外部 API。`PlayerRepository.getUUID(String)`(:116-143) 已存在（启动预热缓存 + 大小写不敏感 + 无 Mojang 阻塞），可直接复用，**无需改 DB 代码**。

### 改动

`island/IslandManager.java`：重写 `getIslandByPlayerName`(:290-298)，移除 `@SuppressWarnings("deprecation")`：
```java
public Optional<Island> getIslandByPlayerName(String playerName) {
    Player online = Bukkit.getPlayerExact(playerName);
    if (online != null) {
        return getIsland(online.getUniqueId());
    }
    return playerRepository.getUUID(playerName).flatMap(this::getIsland);
}
```
（`playerRepository` 字段已在 IslandManager 中注入；具体字段名实现时确认。）

**行为变化**：未知玩家（既未在线也不在 DB 缓存）→ 返回 `Optional.empty()`，**不再触发阻塞的 Mojang API 请求**（这正是修复目的）。

---

## 实施顺序

1. **#31 + #32**（Base 架构，改动面最广但机械，先做以稳定基础）
2. **#28**（GridKeys 工具类 + IslandManager 去重）
3. **#26**（IslandManager，与 #28 同文件，紧接其后）
4. **#27**（TaskManager，独立）
5. **#33**（PortalListener，独立）

## 验证

- `./gradlew build`（含 `--no-build-cache --rerun-tasks` 强制重编译）通过，无新增编译错误/警告。
- 预期 deprecation 警告减少（移除 `getOfflinePlayer(String)` 的 `@SuppressWarnings`）。
- grep 复查：
  - 两个 Base 类无残留 `StarMSkyblock.getInstance().getWorldManager()`。
  - 全项目无残留手写 `0xffffffffL` grid-key 编码（IslandManager + IslandDeleteTask 6 处均改 GridKeys.encode；BlockBreakTaskListener 的 `0xFFFF` 保留）。
  - 无残留 `Bukkit.getOfflinePlayer(String)`（按名查）。
  - DropPickupPermissionManager 构造器与其他 11 个一致（4→统一经由 Base 6 参）。

## 不在本次范围

- #16（误报，跳过）。
- DB 相关 HIGH 项（#3/#7/#8/#9/#10）——受 DB 代码冻结约束，待解除后单独推进。
