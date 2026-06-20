# TODO

---

## 代码质量与性能优化清单

源自 2026-06-18 全仓库审计，37 项，按 P0–P3 分级。每项标注 `file:line`。建议分批 PR：先 P0 正确性，再 P1 热路径/DB/启动，再 P2
重构，最后 P3 配置构建。

### P0 — 严重（正确性 bug，可能丢数据/崩服）

- [x] **1. `TaskManager` 内层 HashMap 并发修改** — `task/TaskManager.java:47,129,140-165,168`。`playerProgress` 外层
  `ConcurrentHashMap`，内层是 `HashMap`；事件监听器主线程写、异步保存线程 `gson.toJson` 迭代 → CME / 丢数据 / 扩容死循环。改
  `ConcurrentHashMap` 或保存时快照。
- [x] **2. `PlayerRepository` LRU 缓存非线程安全** — `database/PlayerRepository.java:23-25,116-117,213-244`。三个
  `accessOrder=true` 的 `LinkedHashMap`，`get()` 改链表；`getUUID` 锁外读；`isFirstNetherJoin` readLock 内 `put`。换
  `ConcurrentHashMap` + 手动 LRU 或 Caffeine。
- [x] **3. 非 FAWE 环境下异步删岛改世界** — `island/IslandDeleteTask.java:66-76` →
  `schematic/SchematicManager.java:187,249`。无 FAWE 时回退到标准 WorldEdit `Operations.complete`，从异步线程改 chunk →
  区块损坏。非 FAWE 路径必须回主线程执行。
- [x] **4. `SQLiteManager.executeInTransaction` 回滚泄漏** — `database/SQLiteManager.java:177-194`。只 catch
  `SQLException`，callback 抛 `RuntimeException` 时 `rollback()` 不执行，`finally` 改回 `autoCommit=true` 静默提交部分写入。catch
  `Throwable` 确保异常路径都 rollback。
- [x] **5. `BlockBreakTaskListener` check-then-act 竞态** — `task/listener/BlockBreakTaskListener.java:49-53,88-90`。
  `inner.isEmpty()` 与 `remove(ck)` 之间另一线程可 `computeIfAbsent().add()` 被抹掉 → 误判为自然方块。用 `compute` 原子操作。
- [x] **6. `IslandManager.createIsland` TOCTOU** — `island/IslandManager.java:192-235`（从 `IslandCreateTask.java:61`
  异步调用）。`containsKey` 与 `put` 之间无锁，两次 `/is create` 可同时通过。用 `putIfAbsent` 或对 ownerId 加
  `synchronized`。
- [x] **7. `SchematicManager.getSignOffsets` 缓存写非原子** — `schematic/SchematicManager.java:68-136,175-177`。
  `schematicCache.put` 与 `signOffsetCache.put` 两次独立写入，`getSignOffsets` 可观察 schematic 已缓存但 offset 为空 →
  NPE 或漏 sign。用单一 `ConcurrentHashMap<String, SchematicEntry>` 打包 + `computeIfAbsent` 串行化。
- [x] **8. `IslandManager.updateIslandSettings` 签名误导** — `island/IslandManager.java:405-412`。接收 `Island island`
  参数却从不使用，实际持久化 `stored.getSettingsJson()`。确认调用方语义，改用 `island.getSettingsJson()` 或移除参数。

### P1 — 性能（热路径 / DB / 启动）

- [x] **9. `IslandRepository` 未缓存 PreparedStatement** — `database/IslandRepository.java`（20+ 处
  `conn.prepareStatement`）。每次写入重新 prepare，叠加全局 writeLock 是写吞吐主瓶颈。在 `SQLiteManager` 维护
  `Map<String, PreparedStatement>` 缓存。
- [x] **10. `BasePermissionManager` 每次权限检查都 `Bukkit.getPlayer`** — `permission/BasePermissionManager.java:97` 及
  12 个子监听器；`ContainerPermissionManager.java:77-227` 单事件最多 7-8 次重复检查。入口解析一次 Island+Player，通过
  `PermissionCheckResult` 复用。
- [x] **11. `location.getChunk()` 强制加载区块** — `permission/BasePermissionManager.java:82`，
  `setting/BaseSettingManager.java:70-71,99-101,121-123`，`listener/BorderListener.java:158-159`。改
  `location.getBlockX() >> 4` 位运算。`PlayerMove` 热路径尤其严重。
- [x] **12. `BorderListener.borderCache` 在 `PlayerMove` 上 LRU 修改** —
  `listener/BorderListener.java:42-47,60,108-126`。`accessOrder=true` 的 `LinkedHashMap`，`getOrDefault` 改链表；reload 迭代有
  CME 风险。改 `ConcurrentHashMap` 或 Caffeine。
- [x] **13. `onEnable` 阻塞主线程** — `StarMSkyblock.java:121,124,134,295-299,516-546`。`warmUpPlayerNameCache`、
  `preWarmWorlds`、`extractSchematics`、`extractSkyblockMenu` 的 `Files.walk` 都同步在主线程。预热移到异步；`Files.walk`
  Stream 放进 try-with-resources。
- [x] **14. `TaskManager.incrementNaturalProgress` 全类型扫描** — `task/TaskManager.java:181-242`。非 BLOCK
  类型每次事件遍历该类型所有任务。维护 `Map<TaskType, List<Task>>` 中只放玩家未完成任务，完成后移除。
- [x] **15. `SkyblockExpansion` placeholder if 链** — `integration/SkyblockExpansion.java:80-424`。每次 PAPI 请求 O(n)
  `equalsIgnoreCase`；`LazyContext.resolveChunk` 可能主线程加载区块；`LazyContext.playerIsland` 非 `volatile`。改
  `Map<String, Function>` 派发；加 `volatile`；位运算。
- [x] **16. `IslandManager.loadIslandsFromDatabase` 启动期 N 次写入** — `island/IslandManager.java:133`。加载循环内对每个权限为空岛屿调用
  `savePermissions`。启动期跳过或单次批量 INSERT。
- [x] **17. `MessageUtil.colorize` 全 Component 往返** — `util/MessageUtil.java:52-63,117-120`。每次 `colorize` 都
  `parse` → serialize 回 legacy 字符串。热路径缓存渲染结果或保留 `Component`。
- [x] **18. `TagContentExtractor` 二次复杂度** — `util/TagContentExtractor.java:75,85,94,114-130`。`List<Integer[]>` 装箱；
  `toLowerCase()` 在循环内；子串匹配 O(n*m)。一次 `toLowerCase`；`int[]` 替代 `Integer[]`；预编译 `Pattern`。

### P2 — 代码质量 / 可维护性

- [x] **19. 12 个权限监听器重复样板** — `permission/*PermissionManager.java`。构造器、`checkPermission`→`setCancelled`→
  `sendDenyMessage` 流程一致。抽 `AbstractPermissionListener` + `Map<Event, IslandPermission>` 派发；
  `ContainerPermissionManager` 多分支合并。
- [x] **20. `IslandManager` 索引清理重复** — `island/IslandManager.java:686-705 vs 715-740`。`deleteIsland` 与
  `removeIslandFromMemory` 几乎相同的 6 索引清理。抽 `removeFromIndices(Island)`。
- [x] **21. 手拼 JSON** — `island/IslandManager.java:384-385`，`island/Island.java:524-525`。字符串拼 JSON，NaN/Infinity
  生成非法 JSON。复用 GSON。
- [ ] **22. `Bukkit.getOfflinePlayer(name)` 主线程阻塞** — `island/IslandManager.java:282`，
  `task/TaskManager.java:529,543`。已废弃 API，未知名字触发 Mojang 阻塞查询。用 `PlayerRepository` 缓存或异步解析后回调。
- [x] **23. `IslandDeleteTask` 魔法数与低效检测** — `island/IslandDeleteTask.java:90-99`。`-64`/`320` 硬编码；
  `BoundingBox.contains(Vector)` 遍历在线玩家。统一用 world 高度；改 `isPlayerOnIsland` O(1) chunk 数学。
- [x] **24. `IslandCreateTask` 三重嵌套 `BukkitRunnable`** — `island/IslandCreateTask.java:109-134`。三世界连续粘贴用三层匿名
  Runnable，nether/end 的 `pasteSchematic` 返回值被忽略 → 部分失败无感知。抽方法串行调度，失败记日志/回滚。
- [x] **25. `InvitationManager.cleanupExpiredInvitations` 未调度** — `island/InvitationManager.java:193`。方法定义但
  CLAUDE.md 提到的 5 分钟清理需确认是否真的接上 scheduler。（复查确认：`StarMSkyblock.initInvitations()` 已用 `runTaskTimer`
  接 6000t 周期，无需修改。）
- [x] **26. `SchematicManager` 静默吞 NPE** — `schematic/SchematicManager.java:146-152`。bare
  `catch (NullPointerException)` 触发 Sponge 重试，掩盖无关 NPE。显式判空。
- [x] **27. `SkyblockExpansion` 多处 `catch (Throwable ignored)`** —
  `integration/SkyblockExpansion.java:419,456,498,524,563,589,617,821,850-853,856-861`。吞 `Error`，排障困难。至少
  `consoleWarn` 一行；只 catch 预期类型。（复查确认：所有 catch 块均已 `consoleWarn/Error`，无空 catch，无需修改。）
- [x] **28. `SQLiteManager.batchWrite` 死分支** — `database/SQLiteManager.java:196-218`。外层 `catch (SQLException)`
  不可达（内层已转 RuntimeException）。删除死代码或重写异常流。
- [x] **29. `ConfigManager` 配置 key 字符串散落** — `config/ConfigManager.java:97-244` 等处。
  `config.getInt("island-radius", 5)` 字面量散落，拼写错误静默回退；无 `reload()` 入口。抽 `ConfigKeys` 常量类；提供
  `reload()` 级联下游。
- [x] **30. `IslandCommand` 重复实例化** — `command/IslandCommand.java:38`。`PromoteDemoteCommand` 实例化两次。共用一个实例。
- [x] **31. `BlockBreakTaskListener` chunkKey 仅 8 位 worldIndex** — `task/listener/BlockBreakTaskListener.java:75-77`。超
  256 世界静默碰撞。改 16 位或断言。

### P3 — 构建 / 配置 / 依赖

- [x] **32. `plugin.yml` WorldEdit 应为 `depend` 而非 `softdepend`** — `src/main/resources/plugin.yml:8`，
  `StarMSkyblock.java:119`。硬依赖但声明为 softdepend，加载顺序不佳会在 `onEnable` 抛错。改 `depend: [WorldEdit]`。
  （复查确认：保留 `softdepend`，不改 `depend`。原因：`SchematicManager` 反射同时支持 FAWE 和 WorldEdit，FAWE-only
  服务器不一定注册 `WorldEdit` 插件名，`depend: [WorldEdit]` 会拒绝加载。已在 `softdepend` 列表前部加 `FastAsyncWorldEdit`
  让 FAWE 优先于本插件加载，配合 `onEnable` 的 WorldEdit/FAWE 缺失检测，等价硬依赖但兼容 FAWE-only 部署。）
- [x] **33. `plugin.yml` 缺 FastAsyncWorldEdit / Multiverse-Core 软依赖** — `plugin.yml:8`。运行时检测但未声明。补
  `softdepend: [FastAsyncWorldEdit, WorldEdit, Multiverse-Core, PlaceholderAPI, TrMenu, Vault, AuraSkills]`。
- [x] **34. Multiverse `loadbefore` 与运行时检测矛盾** — `plugin.yml:7`，`world/SkyblockWorldManager.java:107-116`。
  `loadbefore` 保证本插件先加载 → `isPluginEnabled("Multiverse-Core")` 在 `onEnable` 必为 false → Multiverse import
  分支永不执行。删除 `loadbefore`，改 `softdepend`。
- [x] **35. Java 25 / Spigot 26.1.2 工具链超前** — `build.gradle:11`，`plugin.yml:5`。主流 Paper 仍以 Java 21 /
  1.20.x-1.21.x 为目标，26.1.2 未发布。评估必要性，若不需要降到 Java 21 + 当前稳定 Spigot API。
  （复查确认：不再适用。项目有意选择 Java 25 / Spigot 26.1.2，代码已用 Java 21+ 语法（switch pattern、record、`var` 等），
  用户确认保持现状。）
- [x] **36. `StarMSkyblock.java` 匿名 `PlayerJoinEvent` 监听未注销** — `StarMSkyblock.java:260-268`，`onDisable` 只
  `cancelTasks`。`/reload` 后旧监听仍挂 Bukkit → 内存泄漏 + 重复触发。用具名 `Listener` 字段，`onDisable` 调
  `HandlerList.unregisterAll(this)`。
- [x] **37. TrMenu hook 无 reload 安全** — `StarMSkyblock.java:270-278`。若 TrMenu 后加载，
  `JavaScriptAgent.INSTANCE.putBinding` 不会重跑；未判 `INSTANCE` 非 null。暴露 `hookTrMenu()` 方法，TrMenu 加载完成事件后再调一次。
  （实施：抽 `hookTrMenu()` 方法并 `try/catch Throwable` 兜 `NoClassDefFoundError`/`LinkageError`；新增具名
  `trMenuLateLoadListener` 监听 `PluginEnableEvent`，TrMenu 后加载时调一次 `hookTrMenu()`。`putBinding` 走 `Map.put`
  幂等，`extractSkyblockMenu` 内部判存在，重复调用安全。）

### 建议实施顺序

1. **第一批 P0（正确性优先）**：项 1–8，各自隔离，可单独 PR。
2. **第二批 P1 性能热路径**：项 10, 11, 12, 15。监听器优化，用 spark profiler 对比前后。
3. **第三批 P1 DB 与启动**：项 9, 13, 14, 16。PreparedStatement 缓存 + 启动异步化。
4. **第四批 P2 代码质量**：项 19, 20, 21, 22, 24, 26, 27, 28。重构为主，逐文件处理。
5. **第五批 P3 配置/构建**：项 32–37。单 PR 改 `plugin.yml`/`build.gradle`/`onDisable`。

### 验证方式

- **并发 bug**：启用 `-ea`，写 stress 测试模拟多线程同时 `createIsland` / `incrementNaturalProgress`，观察是否抛 CME。
- **DB 性能**：岛屿数 1000+ 时测 `/is create` 平均耗时，对比 PreparedStatement 缓存前后。
- **监听器热路径**：用 `spark` profiler 在 50 玩家在线下采样，观察 `BlockBreakEvent` 处理时间占比。
- **启动**：测量 `onEnable` 总耗时，目标从同步预热降到 < 200ms 主线程占用。
- **回归**：手动覆盖 `/is create`/`/is delete`/`/is task submit`/`/is upgrade`/PAPI 占位符/Multiverse 世界导入。