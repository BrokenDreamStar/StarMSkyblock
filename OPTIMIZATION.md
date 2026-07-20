# StarMSkyblock 优化建议

> 生成日期：2026-07-09
> 分析方式：对 141 个 Java 源文件 + 构建配置 + 资源文件进行三维度并行审查（性能与并发 / 代码质量与架构 /
> 构建与资源管理），交叉验证后去重汇总。
> 严重度：🔴 HIGH（正确性/数据风险/明显卡顿） · 🟡 MEDIUM（维护性/潜在问题） · 🟢 LOW（清理/微优化）

---

## 目录

- [一、最高优先级问题速览](#一最高优先级问题速览)
- [二、并发与线程安全](#二并发与线程安全)
- [三、数据库与主线程性能](#三数据库与主线程性能)
- [四、等级系统](#四等级系统)
- [五、配置与重载](#五配置与重载
- [六、内存与资源泄漏](#六内存与资源泄漏)
- [七、代码质量与可维护性](#七代码质量与可维护性)
- [八、构建与部署](#八构建与部署)
- [九、已经做得好的地方（避免误"修"）](#九已经做得好的地方避免误修)
- [十、优先级路线图](#十优先级路线图)

---

## 一、最高优先级问题速览

| # | 严重度 | 问题                                                             | 位置                                                                                               |
|---|-----|----------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| 1 | 🔴  | `BasePermissionManager` 共享可变状态跨玩家串扰，拒绝消息可能发错对象                 | `BasePermissionManager.java:54-58,151-262`                                                       |
| 2 | 🔴  | `/isadmin reload` 不重载 `AuraSkillsContributionConfig`，却报"全部已重载" | `ReloadCommand.java:19-27`                                                                       |
| 3 | 🔴  | 几乎所有 DB 写操作都在主线程同步执行                                           | `IslandRepository.java`、`PlayerRepository.java`、`LevelManager.java:156-169`                      |
| 4 | 🔴  | 等级扫描每 tick 在主线程同步加载 48 个区块 + 分配快照                              | `IslandLevelCalculator.java:143-179`                                                             |
| 5 | 🔴  | mcMMO 离线 PowerLevel 查询阻塞主线程（每个离线成员一次磁盘读）                       | `McMMOIntegration.java:44-86`                                                                    |
| 6 | 🔴  | i18n 迁移未完成，~210 处硬编码中文绕过消息系统                                   | `IslandCreateTask.java`、`IslandDeleteTask.java`、`PortalListener.java`、`SkyblockExpansion.java` 等 |
| 7 | 🔴  | `SkyblockExpansion` 6+ 处重复的岛屿查找回退模式（~400 行复制）                  | `SkyblockExpansion.java:407-813`                                                                 |
| 8 | 🔴  | `resolve()` 中 `location.getWorld().getName()` 无 null 检查，NPE 风险 | `BasePermissionManager.java:155,188`                                                             |

---

## 二、并发与线程安全

### 1. 🔴 BasePermissionManager 共享可变状态跨玩家串扰

**位置**：`permission/BasePermissionManager.java:54-58, 151-184, 236-262`

**问题**：实例字段 `lastCheckWasAreaLocked`、`lastCheckWasPublicArea`、`lastAreaLockedIsland` 在 `resolve()`/
`resolveOffline()` 写入，在 `sendDenyMessage()` 读取。12 个子管理器各为全局单例，跨所有玩家复用同一组字段。结果：玩家 A
的一次"locked-area"检查会把字段置为 true，紧接着玩家 B 的权限检查在读取时拿到 A 的状态，**收到错误的拒绝原因**（被提示"
locked-area"而实际是普通无权限）。此外 `lastDenyMessageTime`（`:47-52`）是 `accessOrder=true` 的 `LinkedHashMap`，**未**用
`Collections.synchronizedMap` 包裹（对比 `PlayerRepository.createBoundedCache` 已正确处理）。

**修复**：把这三个字段移入 `PermissionCheckResult`（已封装大部分检查状态），每次检查自带上下文；`lastDenyMessageTime` 改
`ConcurrentHashMap` 或参照 `createBoundedCache` 同步包裹。

### 2. 🔴 mcMMO 离线 PowerLevel 查询阻塞主线程

**位置**：`integration/McMMOIntegration.java:44-86`；`level/LevelManager.java:129-163`

**问题**：`LevelManager.onCalculationComplete` 在主线程（`runTask` 调度）执行。当 `useMcMMO=true` 时调用
`McMMOIntegration.getIslandResult(island)`，该方法是**同步**的（返回 `CompletableFuture.completedFuture(...)`），循环对每个离线成员调用
`ExperienceAPI.getPowerLevelOffline(uuid)` —— mcMMO 离线 API 同步读它自己的 SQLite/H2。N 个成员 = N 次主线程磁盘读。由于
future 已完成，`thenAccept` 也在主线程同步执行，整条读路径阻塞主线程直到完成。

**修复**：用 `CompletableFuture.supplyAsync(...)` 包裹 `getIslandResult` 方法体，让离线 API 调用离开主线程，与 AuraSkills
异步路径对齐。

### 3. 🟡 SQLite 单 Connection 被多线程共享，readLock 并发不安全

**位置**：`database/SQLiteManager.java:193-195, 204-231`

**问题**：单个 JDBC `Connection` 被所有读写共享。`ReentrantReadWriteLock` 允许多个并发 reader，但 SQLite JDBC `Connection`
**不是线程安全**的 —— 两个 reader 会互相破坏 `Statement`/`ResultSet` 状态。`PlayerRepository.warmUpPlayerNameCache()`
明确异步调度（`StarMSkyblock.java:218`），`TaskManager.saveAllDirty/saveAll` 也异步运行（`TaskManager.java:115-116`
）；若玩家在启动预热期间加入，主线程 `getBorderEnabled` 与异步预热会同时处于 `readLock` 内、共享同一 `Connection`。

**修复**：二选一 —— (a) 引入连接池（HikariCP）让每线程独立连接；(b) 禁止并发 reader，所有读也走 `writeLock`（反正底层
Connection 也不支持并发读）。

### 4. 🟡 preparedStatementCache 是裸 HashMap，无内部锁

**位置**：`database/SQLiteManager.java:28, 171-178`

**问题**：`prepareCached()` 的 `get`/`put` 未在 `dbLock` 内（仅靠调用方自觉持锁），任一调用方忘记加锁就会损坏 map（resize
时死循环/丢条目）。仓储层目前一致持锁，但 API 契约脆弱。

**修复**：`prepareCached` 内部自行取锁，或改 `ConcurrentHashMap`。

### 5. 🟡 IslandLevelCalculator.processSnapshots 的 chunksScanned 竞态

**位置**：`level/IslandLevelCalculator.java:322-336`

**问题**：每 tick 提交的 async 任务若未完成，下一 tick 又会调度一个，二者在 Bukkit 异步池并发执行。
`chunksScanned += tasks.size()` 是非同步 read-modify-write，会丢增量。当前 `sendProgress` 是空 stub（见 #33），故无可见影响；一旦实现
`sendProgress` 调用 `MessageUtil.send`，还需切回主线程。

**修复**：`chunksScanned` 改 `AtomicInteger` 并 `addAndGet`，或把自增移入已有的 `synchronized (rawTotalCounts)` 块。

### 6. 🟢 LevelManager.cooldowns 是裸 HashMap 且无界

**位置**：`level/LevelManager.java:52`

**修复**：`ConcurrentHashMap` + 容量或时间淘汰（如清除超过 `2 * cooldownSeconds` 的条目）。

---

## 三、数据库与主线程性能

### 7. 🔴 几乎所有 DB 写操作都在主线程

**位置**：`database/IslandRepository.java`（所有 `updateX`：`updateRadius:179-194`、`updateName:213-228`、
`updateGeneratorLevel:230-245`、`updateHomeData:264-279`、`updateSettings:297-312`、`savePermissions:314-329`、
`updateLevel:575-593`、`updateBaseline:547-563`）；`database/PlayerRepository.java`（`savePlayerName:60-78`、
`setBorderEnabled:193-209`、`setFirstNetherJoin:246-263`、`saveTasks:292-308`）；`level/LevelManager.java:156-161,166-169`

**问题**：每个变更方法在持 `dbLock.writeLock()` 时同步 `executeUpdate()`，且从主线程事件处理器/命令路径调用。热路径示例：
`BorderListener.onPlayerJoin`（`:69-87`）每次加入最多 3 次串行主线程 DB 操作（`getBorderEnabled` → `savePlayerName` → 可选
`setFirstNetherJoin`）；`/is level` 完成后 `islandRepo.updateLevel(...)` 也在主线程；`PortalListener.tryUnlockNether`
首次进下界时主线程写库。`synchronous=NORMAL` 下每次写约 1–10ms，并发加入突发会产生明显登录卡顿。

**修复**：(a) 批量 + 调度到异步执行器（已有 `batchSaveTasks`/`batchSavePermissions` 模式可推广）；(b) 主线程读应命中已有内存缓存，并给
`PlayerRepository` 加 `borderEnabledCache` 消除 join 时读。

### 8. 🔴 等级扫描每 tick 主线程同步加载 48 个区块 + 分配快照

**位置**：`level/IslandLevelCalculator.java:143-179`

**问题**：每 tick 取 `BATCH_SIZE=16` 位置 × 3 世界 = 最多 48 次 `world.getChunkAt()`（同步磁盘读，未驻留时触发 IO）+ 48 次
`chunk.getChunkSnapshot()`（同步拷贝 16×16×(minY..maxY) 到新对象，每快照 ~98K 条目）。默认半径 8 → 867 位置 ≈ 2.7s 纯
IO；最大半径 32 → ~12675 位置 ≈ 40s 主线程区块加载。另：`LevelManager.CHUNKS_PER_TICK=16`（`:41`）是**死代码**（从不读取），实际速率由
`BATCH_SIZE` 控制。

**修复**：(1) 用 Paper 的 `getChunkAtAsync(x, z, consumer)` 把区块加载移出主线程；(2) 调大 `BATCH_SIZE` 到
32–64（配合异步加载）；(3) 移除死常量 `CHUNKS_PER_TICK` 或真正接线；(4) `finishPhase` 不直接碰 Bukkit API（只用 `MessageUtil`
和仓储写），可整体移到异步线程。

### 9. 🟡 executeInTransaction 对只读回调也取 writeLock，阻塞所有读

**位置**：`database/SQLiteManager.java:204-231`

**问题**：`executeInTransaction` 无条件取 `writeLock`，且在回调体执行期间持有，与 `readLock` 互斥。长时间批量写会阻塞所有
`getPlayerName`/`getUUID`/`isFirstNetherJoin` 读。

**修复**：区分只读事务用 `readLock`（需先解决 #3 的 Connection 共享问题，否则无意义）。

### 10. 🟡 PlayerRepository 未用 prepareCached，每次重编译 SQL

**位置**：`database/PlayerRepository.java` 多处 `conn.prepareStatement(sql)`（
`:68,94,126,152,177,199,227,253,276,298,316`）

**问题**：`SQLiteManager.prepareCached`（`:171-178`）完全没被 `PlayerRepository` 使用，每次调用重新解析/规划 SQL。
`IslandRepository` 多数正确用了缓存。更糟：`batchSaveTasks`（`:316`）在 try-with-resources 里关闭了缓存语句，**破坏缓存**
导致下一个调用方拿不到。

**修复**：改用 `sqliteManager.prepareCached(sql)`，且**不要**关闭缓存语句（生命周期由缓存管理）。

### 11. 🟡 IslandLevelCalculator 递减收益循环 O(overLimit)

**位置**：`level/IslandLevelCalculator.java:226-234`

**问题**：`for (long i=0; i<overLimit; i++) exp += Math.round(Math.max(expValue/(1+decay*i), minimum));` 超阈值方块逐个累加。默认仅
3 种方块有阈值（封顶 30k–50k），可忽略；但若运维设低阈值（如 `COBBLESTONE: 1000`）且岛屿有百万圆石，则变成主线程百万次循环。

**修复**：闭式近似（调和级数 `Σ expValue/(1+decay*i)` 有 digamma 近似）或预计算查表；或把 `finishPhase` 移到异步（见 #8）。

---

## 四、等级系统

（#8、#11、#5 见上方各节）

### 12. 🟢 IslandLevelCalculator 每快照分配 ~98K 条目，内存抖动

**位置**：`level/IslandLevelCalculator.java:161-167`

**修复**：直接遍历区块 section palette（NMS/反射），仅拷贝调色板索引数组（远小于完整快照），仅对非 air 块处理。较大重构。

### 13. 🟢 LevelResults / Island 跨线程变更无安全发布

**位置**：`level/LevelResults.java`；`level/IslandLevelCalculator.java:59,64,322-336`；`level/LevelManager.java:115-172`

**问题**：`LevelResults` 字段是普通非 volatile。`rawTotalCounts`（在 synchronized 块内、且 happens-before `decrementAndGet`
）是安全发布的，但 `chunksScanned`（在 synchronized 块外）不是。

**修复**：`chunksScanned` 改 `AtomicInteger`（同时修复 #5）。

---

## 五、配置与重载

### 14. 🔴 /isadmin reload 不重载 AuraSkillsContributionConfig（假成功）

**位置**：`command/subcommand/ReloadCommand.java:19-27`；`StarMSkyblock.java:248-249`

**问题**：reload 调用 `experienceConfig.reload()`（重载 `level.yml` 的 `blocks/limits/diminishing/level-cost/baseline`
段），但**从不**重载 `auraskillsContributionConfig` —— 插件上甚至没有 `getAuraskillsContributionConfig()` getter。改
`level.yml` 的 `auraskills.type/coefficient/max-bonus-level/enabled` 后 reload 仍报"所有配置文件已重载！(耗时 Xms)"
，实际无效，需重启。等级结果错误且无任何告警。CLAUDE.md 明确说"新配置文件必须加到这里才能重载" —— 这是潜在的运维陷阱。

**修复**：加 getter + `plugin.getAuraskillsContributionConfig().reload()`。

### 15. 🟡 /isadmin reload 不重载 task 配置（28+ YAML）

**位置**：`ReloadCommand.java:19-27`

**问题**：`TaskConfigScanner` 有 `scan()` 且插件暴露 `getTaskConfigManager()`，但 reload 从不调用。改任务
YAML（新章节、改需求/奖励）不生效，需重启。任务系统是配置最重的子系统。

**修复**：加 `plugin.getTaskConfigManager().scan()`。**注意**：`TaskManager` 内存中的进度引用旧 scan 的 `TaskDefinition`
对象，reload 需重新解析定义，需先评估影响。

### 16. 🟡 reload 非原子，在途操作可能看到新旧配置混合

**位置**：`ReloadCommand.java:19-27`

**问题**：9 个 config manager 顺序重载无同步屏障。reload 窗口内，一次权限检查可能读到刚重载的 `permissionConfigManager`
却仍读旧 `publicAreaConfigManager`/`lockedAreaConfigManager`（它们包装 permission config 且在序列后部重载）。可能产生短暂的权限不一致（绕过或误拒）。

**修复**：reload 期间设 `volatile reloading` 标志让权限监听器短路；或整体快照原子替换。

### 17. 🟡 level.yml 的 auraskills: 段名在 type:mcmmo 时误导

**位置**：`src/main/resources/level.yml:1069-1083`

**问题**：默认 `type: mcmmo`，但外层 YAML key 仍是 `auraskills:`，`AuraSkillsContributionConfig` 也无论选哪个技能插件都从
`auraskills` 段读。配置合并重构遗留的命名不一致。

**修复**：改名为 `skill-contribution:` 或类似中性名。

### 18. 🟢 permissions.yml public-area / locked-area ~95% 重复

**位置**：`src/main/resources/permissions.yml:176-449`

**问题**：两块都枚举全部 ~90 权限和 ~12 设置且值相同。新增权限枚举需在两处加相同默认值。

**修复**：共享 `area-defaults:` 块，两区域仅 override 少数差异项（如 `ENDER_CHEST_OPEN`、`FISHING_ROD_USE`）。

### 19. 🟢 提取的内置文件无版本，升级不覆盖

**位置**：`StarMSkyblock.extractSchematics`/`extractSkyblockMenu`；`TaskConfigScanner.extractBundledTasks`；
`LanguageManager.extractBundledZhCN`

**问题**：所有提取器用 `if (target.exists()) skip`，仅文件不存在时拷贝。新版插件更新了 schematic/task/menu/zh_CN
时，老安装永不更新，除非运维手动删文件。

**修复**：嵌入版本标记（写 `.version` 文件或对比 bundled hash），bundled 版本更新时覆盖；或文档说明升级需删旧文件。

---

## 六、内存与资源泄漏

### 20. 🟡 SkullManager.base64Meta 无界，从不淘汰

**位置**：`util/SkullManager.java:44`

**问题**：启动加载 `skin_textures` 全表，每个独特付费玩家加入增长一条（`refreshTexture:92`
），从不淘汰。大服上是与独特玩家数成正比的无界内存泄漏。纹理已持久化到 DB，淘汰仅代价一次回读。

**修复**：LRU（`PlayerRepository.createBoundedCache` 模式）或 `PlayerQuitEvent` 淘汰离线玩家，miss 时回读 DB。

### 21. 🟡 PortalListener.lastPortalEnter / BorderListener.borderCache / islandBorderCache 无界不淘汰

**位置**：`listener/PortalListener.java:46`；`listener/BorderListener.java:45,47`

**问题**：`lastPortalEnter` 每次 `EntityPortalEnterEvent`（含非玩家实体）追加，2 秒去重逻辑不删旧条目；`borderCache` 每次
join 填充，quit 不清；`islandBorderCache` 在岛屿删除时**不失效**（`IslandManager.cleanupIndices` 不通知
BorderListener）。长期运行慢泄漏。

**修复**：`PlayerQuitEvent` 淘汰 `borderCache`/`lastPortalEnter`；`cleanupIndices` 回调失效对应 `islandBorderCache`。

### 22. 🟢 extractSchematics 异步与同步 initSchematicManager 竞态

**位置**：`StarMSkyblock.java:129-132`

**问题**：`extractSchematics` 异步派发后立刻同步跑 `initSchematicManager` 和岛屿加载。新装首次启动、3 个 `.schem`
尚不存在时，若粘贴早于提取完成会报"找不到岛屿结构文件"。实际影响小（玩家 `/is create` 时已完成）。

**修复**：同步提取（3 个小文件 <3KB）或 `getEntry`/`loadEntry` 等待提取的 `CompletableFuture`。

---

## 七、代码质量与可维护性

### 23. 🔴 ~210 处硬编码中文，i18n 迁移未完成

**位置**：`island/IslandCreateTask.java`（~25 处，`:56-276`）、`island/IslandDeleteTask.java`（~7 处，`:116-199`）、
`listener/PortalListener.java`（`:150,189,224,301,303`）、`placeholder/SkyblockExpansion.java`（"公共区域"/"主世界"/"下界"/"
末地"/"已达到最高等级"/"是"/"否"）、`util/OreDisplayName.java`、`permission/IslandPermissionLevel.java`（枚举显示名"岛主"/"
管理员"/…）、`level/LevelManager.java:215`

**问题**：CLAUDE.md 说消息键是事实来源且迁移进行中，但 `IslandCreateTask`/`IslandDeleteTask` 用原始
`player.sendMessage("§a岛屿创建成功！")`，**零** `MessageUtil.send` 调用 —— 完全绕过 i18n 系统，丢失 `{name}`
替换和静默标志机制。PAPI 扩展直接向消费者返回硬编码中文维度名/"公共区域"。

**修复**：迁移到 `MessageUtil.send(player, "key", Map.of(...))` 并在 `messages/zh_CN.yml` 加键。枚举显示名最棘手，可按枚举名在展示时经
`MessageUtil` 解析。

### 24. 🔴 SkyblockExpansion 6+ 处重复的岛屿查找回退模式

**位置**：`placeholder/SkyblockExpansion.java:407-813`

**问题**：`getIslandName`/`getPlayerRole`/`getIslandLevelHere`/`getIslandValueHere`/`getGeneratorLevelHere`/
`getPlayerOwnIslandName`/`getPlayerOwnRole` 各自重写
`getIslandAt → ifEmpty getIslandAtMaxRange → ifEmpty 返回默认 → try/catch RuntimeException → 返回 fallback`，每个 ~30
行近乎相同，仅末尾提取不同。约 400 行复制粘贴。

**修复**：提取 `Optional<Island> findIslandAt(IslandManager, int chunkX, int chunkZ)`（两次查找都试）和泛型
`<T> T withIslandHere(ctx, T fallback, Function<Island,T> extractor)`，每个方法缩到 3–4 行。

### 25. 🔴 resolve() 中 location.getWorld().getName() 无 null 检查（NPE）

**位置**：`permission/BasePermissionManager.java:155,188`；`setting/BaseSettingManager.java:59`（对比 `:92,114` **有**检查）

**问题**：`Location.getWorld()` 在 Bukkit API 可为 null（无世界构造的 Location、世界卸载期间）。`resolve()` 在每次权限检查热路径上无保护调用
`.getName()`，而 `getIslandAtMaxRange`/`getIslandAt` 正确做了 null 检查。不一致 → null world 在权限检查中抛 NPE，随机取消或放行事件。

**修复**：`resolve()`/`resolveOffline()`/`checkSetting()` 加
`World world = location.getWorld(); if (world == null) return ...;`，或推入共享 helper。

### 26. 🟡 IslandManager.getIslandByPlayerName 用 deprecated getOfflinePlayer(String)

**位置**：`island/IslandManager.java:289-296`（`@SuppressWarnings("deprecation")`）

**问题**：玩家未曾加入时，主线程阻塞于可能的 Mojang API 网络调用。被基于名的命令使用。

**修复**：走 `PlayerRepository` 名字缓存（启动已预热）+ 在线 `Bukkit.getPlayerExact`；拒绝未知的离线按名查找而非触发阻塞
Mojang 请求。

### 27. 🟡 TaskManager 奖励发放逻辑在 claimReward 与 giveForceCompleteRewards 间重复

**位置**：`task/TaskManager.java:416-465` vs `562-630`

**问题**：金币存入、物品掉落、命令派发（含 `server:`/`player:`/默认前缀解析）几乎逐字重复。改奖励逻辑需同步改两处。

**修复**：提取 `grantRewards(UUID uuid, TaskReward rewards, boolean requireOnline)`，两路径都调用。

### 28. 🟡 IslandManager.getIslandAt 与 getIslandAtMaxRange 近乎相同；grid-key 编码重复多处

**位置**：`island/IslandManager.java:659-691, 602-617`；`island/IslandDeleteTask.java:135,143`；
`task/listener/BlockBreakTaskListener.chunkKey`

**问题**：两方法仅 `isChunkWithinIsland` vs `isChunkWithinMaxRange` 之差；grid-key 编码
`(((long)gx)<<32)|(gz&0xffffffffL)` 在四处重复。

**修复**：`private Optional<Island> findInGrid(int chunkX, int chunkZ, BiPredicate<Island,int[]> predicate)` 或传
`boolean maxRange` 标志；key 编码集中到 `GridKeys.encodeCell(gx,gz)`。

### 29. 🟡 LevelManager 手写 JSON 序列化（Gson 已可用）

**位置**：`level/LevelManager.java:357-382`

**问题**：`serializeStringMap`/`serializeBlockCounts` 用 `StringBuilder` 手写 JSON，无特殊字符转义（对合法 Material
名低风险但脆弱）。`IslandManager` 已用 `GSON`（`:36`）做反操作。

**修复**：用共享 `Gson`（`new Gson().toJson(map)`）或复用 `IslandSerializer`。

### 30. 🟡 异常静默吞没（含数据丢失风险）

**位置**：

- `task/TaskManager.java:133-137` —— `loadPlayerProgress` 捕获 `gson.fromJson` 的 `Exception` 返回空 map **无日志**。损坏的
  task JSON 静默清空玩家任务进度。
- `database/SQLiteManager.java:246` —— `batchWrite` rollback 失败 `catch (SQLException ignored)`（对比
  `executeInTransaction:219` 有日志）。
- `util/reflection/FaweReflection.java:71` —— `flush` 中 `catch (Exception ignored)`。
- `task/listener/MoneyTaskListener.java:76`、`task/command/TaskCommand.java:183`、`island/IslandSerializer.java:99` ——
  `catch (NumberFormatException/IllegalArgumentException ignored)`。

**修复**：至少 TaskManager JSON 解析失败和 SQLite rollback 失败 WARN 日志；FaweReflection/IslandSerializer 至少
`consoleWarn` 一次。

### 31. 🟡 DropPickupPermissionManager 构造器签名与其他 11 个不一致

**位置**：`permission/manager/DropPickupPermissionManager.java:38-42` vs 其他全部

**问题**：唯一接受 `JavaPlugin plugin` 参数（它要调度任务）。其他 11 个都是 4 参数
`(islandManager, configManager, publicAreaConfig, lockedAreaConfig)`。`IslandPermissionManager:78`
对它的构造方式也与其他不同。无法做反射/列表化注册。

**修复**：把 `plugin` 注入基类（让所有 manager 都能调度），或用 `JavaPlugin.getProvidingPlugin(getClass())`（
`ToolPermissionManager.syncEntityStatusForPlayer:466` 已这么做）。

### 32. 🟡 StarMSkyblock 是 30+ getter 服务定位单例；Base 类走 getInstance() 而非注入

**位置**：`StarMSkyblock.java:410-514`（30+ getter，静态 `instance`）；
`permission/BasePermissionManager.java:156-160,189-193`；`setting/BaseSettingManager.java:60-61,95-97,117`

**问题**：两个 Base 类每次检查都 `StarMSkyblock.getInstance().getWorldManager()` 而非构造器注入（已注入 4 个其他
manager）。把权限热路径耦合到全局单例，且无法单测。12 个子类 × 同一 `getInstance()` 查找。

**修复**：构造器注入 `SkyblockWorldManager`（已传 `islandManager` 等）。顺带让这些类可单测。

### 33. 🟡 PortalListener 坐标计算器互为镜像，中心块计算重复 3 处

**位置**：`listener/PortalListener.java:261-287, 429`

**问题**：`calculateNetherPortalLocation` 偏移 ÷8；`calculateOverworldPortalLocation` 偏移 ×8。中心块计算
`centerChunkX*16+8` 等在两方法 + `getIslandLocation` 重复。

**修复**：提取 `getIslandCenterBlockXZ(Island)` 和 `scalePortalLocation(from, targetWorld, island, double factor)`。

### 34. 🟢 无测试；多个纯逻辑类高价值可测

**位置**：`grid/GridManager.java`（Ulam spiral，纯数学 O(1)）、`level/IslandLevelCalculator.java:340-391`（手写表达式解析器）、
`:244-289`（等级公式，`while(true)` 循环有 `cost<=0` 溢出保护是好测试点）、`permission/IslandPermissionLevel.java`（角色层级）、
`island/Island.java:586-611`（`hasPermission`）、`task/listener/BlockBreakTaskListener.java:68-94`（位打包）

**修复**：`build.gradle` 加 JUnit 5，把等级公式和表达式解析器抽成纯 helper 类，加测试（spiral 对 0–1000 比参考实现；解析器测运算符优先级/括号；
`getManageableRoles`）。

### 35. 🟢 死代码 / 桩

- `level/LevelManager.sendProgress`（`:107-110`）空 stub（"暂时不实现"）。
- `level/LevelManager.getCachedLevel`/`getCachedExperience`（`:263-272`）透传到 `Island` getter，无附加值。
- `task/listener/FarmingTaskListener.onCropGrow`（`:30-35`）调 `incrementProgress(null,...)` 立即 no-op → 整个
  `BlockGrowEvent` handler（每个世界每次作物生长都触发）在 `CROPS.contains` 过滤后是死代码。
- `LevelManager.CHUNKS_PER_TICK` 死常量（见 #8）。
- `StarMSkyblock.java:358,364`、`IslandCommand.java:135` 注释掉的 `MessageUtil.consolePrint`。

**修复**：删注释行；`sendProgress` 实现或删；`onCropGrow` 删除或在 TaskManager 实现非玩家种植进度跟踪（可能是原意）。

### 36. 🟢 SimpleDateFormat 每次 createIsland 新建

**位置**：`island/IslandManager.java:230-231`

**修复**：静态 `DateTimeFormatter`（不可变/线程安全），或存 DB 的 `CURRENT_TIMESTAMP` 默认值。

### 37. 🟢 IslandManager 泄漏 IslandRepository 给 LevelManager 直写

**位置**：`island/IslandManager.java:743-745`；`level/LevelManager.java:157,166,343`

**问题**：`LevelManager` 绕过 manager 直写 level/baseline 到 repo，若 manager 日后加写穿缓存则内存与 DB 会分叉。当前因
`LevelManager` 也直接 `island.setLevel(...)` 而暂时 OK，但写路径分裂脆弱。

**修复**：加 `IslandManager.updateLevel(...)`/`updateBaseline(...)` facade，`LevelManager` 调它们。

### 38. 🟢 MoneyTaskListener 无条件常驻轮询 Vault 余额

**位置**：`task/listener/MoneyTaskListener.java:42-51,24`

**问题**：`startBalanceCheckTask` 调度 `runTaskTimer` 周期 200L（10s），`taskScheduled=true` 永久。即使无 EARN_MONEY 任务配置，也每
10s 遍历在线玩家调 `economy.getBalance(player)` —— 部分 Vault 经济后端（如 MySQL 支撑的）用 DB 查询响应。50 在线 = 每 10s
50 次主线程 DB 查询，永久。`taskScheduled` 从不复位，玩家全离线也不停。

**修复**：仅在配置含 `TaskType.EARN_MONEY` 时调度（`TaskManager:65` 已这样 gate 监听器注册）；无在线玩家时取消。

### 39. 🟢 BasePermissionManager.checkPermission(Location,UUID,...) 多余的 Bukkit.getPlayer 查找

**位置**：`permission/BasePermissionManager.java:96-108`

**问题**：UUID 重载调 `Bukkit.getPlayer(uuid)`（哈希查找），而 `ObsidianToLavaListener.onObsidianClick` 和
`DropPickupPermissionManager.onBundleInteract` 调用点已有 `Player`。`Player` 重载（`:113`）存在且避免此查找。Bundle 交互是较热路径。

**修复**：调用点直接传 `player` 而非 `player.getUniqueId()`。

---

## 八、构建与部署

### 40. 🟡 Shadow 插件产出冗余重复 JAR，输出名与 CLAUDE.md 不符

**位置**：`build.gradle:3, 84-86`；产物在 `build/libs/`

**问题**：所有依赖都是 `compileOnly`，无物可 shade。但构建产出两个近似 JAR：`StarMSkyblock-1.0.0.jar`（592KB，plain `jar`）和
`StarMSkyblock-1.0.0-all.jar`（597KB，shadow），仅差 ~5KB（shadow manifest）。CLAUDE.md 说输出是 `build/libs/StarMSkyblock.jar`
，但该名文件不存在 —— 部署说明会指向错误产物。

**修复**：加 `shadowJar { archiveClassifier = '' }` 并 `jar { enabled = false }` 产单一 `StarMSkyblock-1.0.0.jar`；或直接删
shadow 插件。更新 CLAUDE.md 匹配实际输出名。

### 41. 🟡 未设 options.release，字节码目标 Java 25，与"Java 21+"文档不符

**位置**：`build.gradle:9-13, 66-69`

**问题**：工具链 `JavaLanguageVersion.of(25)`，`JavaCompile` 未设 `options.release`。无 release 标志则字节码目标 Java
25。CLAUDE.md 称"运行于 Paper 1.26.x，Java 21+" —— 不准确，编译类在 Java 21 运行时抛 `UnsupportedClassVersionError`。

**修复**：加 `options.release = 21`（或真实最低版本）到 `JavaCompile` 配置；或修正 CLAUDE.md 说明需 Java 25。

### 42. 🟢 libs/mcMMO.jar 无版本号，破坏可复现构建

**位置**：`build.gradle:34`；`libs/`

**问题**：`compileOnly files('libs/mcMMO.jar')` 引用无版本号的 jar（不像 `AuraSkills-2.3.12.jar`、`TrMenu-3.12.2.jar`）。实际文件
3.6MB 但来源/版本未固定。无 lockfile/checksum。

**修复**：改名 `mcMMO-<version>.jar` 并更新依赖行；文档记录确切来源。

### 43. 🟢 processResources 冗余 schematics include

**位置**：`build.gradle:77-81`

**问题**：schematics 在 `src/main/resources/schematics/`，默认 `processResources` 已包含。显式 `from` 块重加，
`duplicatesStrategy = 'INCLUDE'` 保留两份。功能无害但配置冗余/困惑。

**修复**：删除该块。

### 44. 🟢 plugin.yml 正确完整（无问题）

**位置**：`src/main/resources/plugin.yml`

`api-version: '26.1.2'` 匹配 `spigot-api:26.1.2`。未提交 diff 正确加入 `mcMMO` 到 `softdepend`。两命令两权限声明齐全，
`load: POSTWORLD` 合适。无 `depend:` 块但 WorldEdit/FAWE 运行时检查 + `disablePlugin` 回退可接受。

---

## 九、已经做得好的地方（避免误"修"）

> 审查中发现以下设计已正确，列此以免"优化"反而引入问题：

- **`BasePermissionManager.enforce()` / `BaseSettingManager.checkSetting()`** 成功上提了拒绝消息/区域检查样板；12
  个权限 + 6 个设置监听器**不是**复制粘贴，复用得当。
- **8 个 task listener** 正确用 `BaseTaskListener.track()`。
- **`SQLiteManager.executeInTransaction`**（`:204-231`）`catch (Throwable)` + rollback 是有据的（防 `RuntimeException`
  静默部分提交），`StarMSkyblock.hookTrMenu` 的 `catch (Throwable)` 同理有文档说明。**非**过宽捕获 bug。
- **`ConfigManager`** 用 `volatile` 字段加载时读一次（正确缓存模式；reload 重新填充；无逐访问文件读）。
- **`SkyblockExpansion`** 已把 30 项 `equalsIgnoreCase` 链换成 O(1) `Map` 分发 —— 优秀的前次重构；剩余重复（#24）在 helper
  方法而非分发。
- **`GridManager`** O(1) Ulam spiral，无问题。
- **`PlayerRepository.createBoundedCache`**（`:348-356`）正确把 `accessOrder=true` LinkedHashMap 包 `synchronizedMap`
  并注释说明原因。3 个缓存 LRU 2000 条。
- **`StarMSkyblock.onDisable`** 调 `HandlerList.unregisterAll(this)`（`:400`）正确防 reload 监听器泄漏。
- **`BorderListener.onPlayerMove`** 在 chunk X/Z 不变时短路（`:119`）。
- **`ContainerPermissionManager.onContainerInteract`** 一次 `resolve(loc,player)` 复用 `PermissionCheckResult` 跨 7+ 检查（
  `:100`）。
- **任务写已正确批量**（6000 tick 一次 + `executeInTransaction` + `addBatch`）；SQLite WAL + `synchronous=NORMAL` 无 fsync
  压力。
- **日志一致**：`src/main/` 内零 `getLogger()`/`System.out`，全走 `MessageUtil.consolePrint/warn/error`；missing-key 去重集合
  bounded（有限键空间，reload 清空）。i18n 键完整（无缺失键）。

---

## 十、优先级路线图

### 第一阶段：正确性 / 数据风险（建议先做）

1. **#1** BasePermissionManager 状态移入 `PermissionCheckResult` + 同步 `lastDenyMessageTime`
2. **#14** ReloadCommand 加 `auraskillsContributionConfig.reload()` + getter（假成功陷阱）
3. **#25** `resolve()` 加 world null 检查（NPE）
4. **#30** TaskManager JSON 解析失败至少 WARN（静默清进度 = 数据丢失）
5. **#3/#4** SQLite Connection 并发安全（连接池或全 writeLock）

### 第二阶段：主线程卡顿（影响玩家体感）

6. **#5** mcMMO `getIslandResult` 真正异步
7. **#7** DB 写移出主线程（异步队列/批量调度）+ `borderEnabledCache`
8. **#8** 等级扫描用 `getChunkAtAsync` + 调 BATCH_SIZE + 删死常量

### 第三阶段：内存与稳定

9. **#20** SkullManager.base64Meta LRU
10. **#21** 三个 listener 缓存淘汰 + 岛屿删除失效
11. **#15/#16** reload 加 task 扫描 + reloading 屏障

### 第四阶段：可维护性（降低未来成本）

12. **#23** i18n 迁移收尾（IslandCreate/DeleteTask、PortalListener、SkyblockExpansion）
13. **#24** SkyblockExpansion 提取 helper 去重
14. **#27/#28/#29** TaskManager 奖励、grid-key、JSON 序列化去重
15. **#34** 引入 JUnit 5 + 纯逻辑类测试
16. **#35** 清理死代码

### 第五阶段：构建与发布

17. **#40/#41** Shadow JAR 去重 + `options.release` + 修正 CLAUDE.md
18. **#42** libs/mcMMO.jar 版本化

---

*本报告基于静态代码审查，部分行为性结论（如 mcMMO 离线 API 是否真磁盘读、Vault 后端是否 DB 查询）取决于运行时环境，建议在目标服务器上用
spark/timings 实测验证优先级。*

---

## 十一、实施状态（2026-07-09 实施）

本轮按建议实施了以下改动，全部通过 `./gradlew build` 编译验证。

### 已实施（17 项）

| #       | 建议                          | 实施内容                                                                                                                                                                                             |
|---------|-----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| #1      | BasePermissionManager 状态串扰  | 三个实例布尔字段改为 `ConcurrentHashMap<UUID, PermissionCheckResult> lastCheckResult`（LRU 上限 512），每个玩家读自己的解析结果；`lastDenyMessageTime` 用 `synchronizedMap` 包裹                                                |
| #2      | mcMMO 阻塞主线程                 | `getIslandResult` 用 `CompletableFuture.supplyAsync` 包裹离线查询；成员 UUID 列表在主线程采集避免跨线程读 island 成员集                                                                                                     |
| #4      | preparedStatementCache 线程安全 | 裸 `HashMap` 改 `ConcurrentHashMap`（语句并发使用仍由 `dbLock` 串行化）                                                                                                                                         |
| #5/#13  | chunksScanned 竞态            | `int` 改 `AtomicInteger`（`addAndGet`/`get`）                                                                                                                                                       |
| #6      | cooldowns 非线程安全             | `HashMap` 改 `ConcurrentHashMap`                                                                                                                                                                  |
| #14     | reload 漏重载 auraskills       | 新增 `getAuraskillsContributionConfig()` getter + `ReloadCommand` 调用 `reload()`                                                                                                                    |
| #15     | reload 漏重载 task             | `ReloadCommand` 调用 `getTaskConfigManager().scan()`（已验证 TaskManager 按 ID 查找 def，重载安全）                                                                                                             |
| #25     | resolve() NPE               | `resolve`/`resolveOffline` 加 `location.getWorld()` null 检查（视为空岛世界之外，不阻断）                                                                                                                         |
| #29     | LevelManager 手写 JSON        | `serializeStringMap`/`serializeBlockCounts` 改用静态 `Gson`（顺带修复转义脆弱性）                                                                                                                               |
| #30     | TaskManager JSON 静默丢数据      | `loadPlayerProgress` 解析失败改 `MessageUtil.consoleError(msg, e)` 记录堆栈                                                                                                                               |
| #36     | SimpleDateFormat            | 改静态 `DateTimeFormatter`（不可变/线程安全）                                                                                                                                                                |
| #35（部分） | FarmingTaskListener 死代码     | 删除 no-op 的 `onCropGrow`（`incrementProgress(null,...)` 立即返回）及 `BlockGrowEvent` import                                                                                                             |
| #38     | MoneyTaskListener 轮询        | 经核查：监听器注册已被 `activeTypes.contains(EARN_MONEY)` 门控（TaskManager），"无 EARN_MONEY 仍轮询"的担忧已不存在；无需改动                                                                                                    |
| #20     | SkullManager 无界缓存           | `base64Meta` 改 bounded `synchronizedMap(LinkedHashMap)`（LRU 上限 2000）；新增 `SQLiteManager.getSkinTexture(UUID)`，缓存未命中时优先回退 DB 再请求 Mojang（避免淘汰后重复请求被限流）                                              |
| #21（部分） | listener 缓存无界               | `BorderListener`/`PortalListener` 各加 `PlayerQuitEvent` 处理器淘汰 `borderCache`/`lastPortalEnter`                                                                                                     |
| #40     | Shadow 冗余 JAR               | `shadowJar { archiveBaseName='StarMSkyblock'; archiveClassifier=''; archiveVersion='' }` + `jar.enabled=false` -> 产出单一 `build/libs/StarMSkyblock.jar`（与 CLAUDE.md/README 文档一致，删除了 `-all`/带版本号副本） |
| #41     | 字节码目标 Java 25               | 加 `options.release = 21`（对齐"Java 21+"部署要求；编译通过证明代码无 Java 22+ API）                                                                                                                                |
| #43     | 冗余 schematics include       | 删除 `processResources` 中重复的 `from('src/main/resources'){include 'schematics/**'}` 块（默认已包含）                                                                                                        |

### 明确推迟（需单独决策或大改，未在本轮处理）

| #           | 建议                                                             | 推迟原因                                 |
|-------------|----------------------------------------------------------------|--------------------------------------|
| #3          | SQLite 单 Connection 并发不安全                                      | 需引入 HikariCP 依赖或改全 writeLock，属架构决策   |
| #7          | DB 写移出主线程                                                      | 需异步写队列/批量调度的大规模重构                    |
| #8          | 等级扫描 getChunkAtAsync                                           | 需 Paper API 验证 + 大重构（竞态部分 #5/#13 已修） |
| #9/#10      | executeInTransaction readLock / PlayerRepository prepareCached | 依赖 #3；#10 需修 batch 关闭破坏缓存的 bug       |
| #11         | 递减收益闭式近似                                                       | 仅极端低阈值配置触发，低优先                       |
| #16         | reload 非原子屏障                                                   | 中等改动                                 |
| #17/#18/#19 | 配置层重命名/去重/版本提取                                                 | 需用户决策或配置迁移                           |
| #22         | extractSchematics 竞态                                           | 实际影响小                                |
| #23         | i18n 210 处迁移                                                   | 大工程                                  |
| #24         | SkyblockExpansion 去重                                           | 大重构                                  |
| #26         | getOfflinePlayer(String) deprecated                            | 需重构 name 查找                          |
| #27/#28     | TaskManager 奖励 / grid-key 去重                                   | 中等改动；#28 跨 4 文件坐标语义去重，非 bug          |
| #31/#32/#33 | 构造器注入 / getInstance / PortalListener 去重                        | 架构重构                                 |
| #34         | JUnit 测试                                                       | 独立工程                                 |
| #37         | IslandManager facade                                           | 低                                    |
| #42         | libs/mcMMO.jar 版本号                                             | 需版本信息                                |
| #12         | 快照内存优化                                                         | 大重构                                  |
| #35（其余）     | 注释代码 / sendProgress stub                                       | 琐碎；sendProgress 为有意保留的扩展点            |

> 建议优先处理推迟项中的 **#3 + #7**（主线程 DB 卡顿的根本原因）和 **#8**（大半径岛屿等级扫描卡顿），这三项需架构决策，建议结合
> spark/timings 实测后单独推进。

---

## 十二、#8 实施更新（2026-07-09 补充）

#8（等级扫描区块异步加载）已实施，采用**反射调用 Paper 运行时 getChunkAtAsync** 的变体（原计划"直接调用"在当前编译依赖下不可行）：

- **原因**：项目编译依赖是 `org.spigotmc:spigot-api:26.1.2`，其 `org.bukkit.World` 接口**没有** `getChunkAtAsync`（Paper 独有
  API；Context7 的 snippet 与 spigot-api 实际不符，已反编译 jar 确认 World 仅有同步 `getChunkAt`）。
- **方案**：新增 `IslandLevelCalculator.getChunkAtAsyncCompat(World,int,int,Consumer<Chunk>)`，反射查找并调用 Paper 的
  `getChunkAtAsync(int,int,Consumer)`（优先）或 4 参数 `includeNeighbors` 重载；`Method` 缓存（每实例查一次），callback 由
  Paper 在主线程执行；非 Paper 或反射失败时回退同步 `getChunkAt`（保持原行为，仅放弃异步收益）。
- **风格**：符合项目"反射兼容跨版本"约定（同 FaweReflection / EnderDragonReflection / WorldBorderReflection）。
- **涉及文件**：`level/IslandLevelCalculator.java`（重写 `loadingPhase` + 新增 `getChunkAtAsyncCompat` + 反射缓存字段）；
  `level/LevelManager.java`（删除死常量 `CHUNKS_PER_TICK`）。
- **验证**：`./gradlew build` 通过，产出单一 `StarMSkyblock.jar`。
- **运行时待办**：在 Paper 服务器跑 `/is level`（大半径岛屿），确认控制台无"反射调用 getChunkAtAsync 失败"
  日志（即反射成功走异步）；若出现该日志说明 Paper 无匹配签名，会回退同步（不影响正确性，仅无性能收益）。

---

## 十二(更新)、#8 已回退（2026-07-09）

用户反馈"不需要多版本兼容"，#8 的反射实现已全部回退：

- 删除反射方案（`getChunkAtAsyncCompat` helper、反射缓存字段、`Method`/`Consumer` import）；`loadingPhase` 恢复为原同步实现（
  `world.getChunkAt` 同步加载 + 同步 snapshot + 直接提交异步 `processSnapshots`）。
- 保留与 #8 无关的 #5/#13 修复（`chunksScanned` AtomicInteger）。
- `LevelManager.CHUNKS_PER_TICK` 死常量已恢复（忠实还原 #8 全部改动；它仍是死代码，可单独清理，归 #35）。
- 回退原因：spigot-api 编译期无 `getChunkAtAsync`（Paper 独有 API），反射调用方案被否决。若未来仍想做 #8，需把编译依赖从
  `org.spigotmc:spigot-api` 切到 `io.papermc.paper:paper-api`（版本坐标需确认）后才能直接调用 `getChunkAtAsync`，无需反射。
- `./gradlew build` 通过。
- 已删除计划文件 `docs/superpowers/plans/2026-07-09-level-scan-async.md`。

---

## 十三、#23 + #24 实施（2026-07-10）

本轮实施 #23（i18n 迁移收尾）与 #24（SkyblockExpansion 去重），全部通过 `./gradlew build` 编译验证（含
`--no-build-cache --rerun-tasks` 强制重编译），zh_CN.yml 经 YAML 语法校验。

### #23 i18n 迁移（约 75 处 -> 消息键）

迁移的 7 个文件，玩家面向硬编码中文已全部转为消息键：

| 文件                           | 迁移内容                                                                                                                                                      |
|------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `IslandPermissionLevel.java` | `getDisplayName()` 改走 `MessageUtil.format("role.<name>")`；删除 `displayName` 字段。`fromString()` **不变**（内部经 `getDisplayName()` 反查中文，行为字节级不变，与 DB role 存储格式无关） |
| `OreDisplayName.java`        | 枚举去 `chineseName` 字段，仅作"可翻译材质注册表"；`toChinese()` 走 `material.<name>` 键，未知材质回退原名（避免 missing-key 警告）                                                         |
| `LevelManager.java` (L219)   | 超阈值方块标题行改用 `level.blocks-over-limit-header` 键                                                                                                             |
| `IslandCreateTask.java`      | 私有 `sendMessage(String)` 改 `sendKey`，~19 处消息走 `island.create.*` 键（含管理员广播）；顺带修复 `-s` 静默标志丢失                                                                |
| `IslandDeleteTask.java`      | 6 处消息走 `island.delete.*` 键                                                                                                                                |
| `PortalListener.java`        | 7 处消息走 `island.portal.*` 键；`checkIslandBounds` 返回键而非文案                                                                                                    |
| `SkyblockExpansion.java`     | 17 处 PAPI 返回值（公共区域/维度名/已达到最高等级/岛屿#/是/否）走 `placeholder.*`/`dimension.*` 键；role 显示经 `getDisplayName()` 自动迁移                                                 |

新增消息键组：`role.*`(6)、`dimension.*`(3)、`material.*`(20)、`placeholder.*`(5)，及 `island.create.*`/`island.delete.*`/
`island.portal.*` 扩展键、`level.blocks-over-limit-header`。

**YAML 陷阱修复**：`placeholder.yes`/`no` 被 YAML 1.1（SnakeYAML）解析为布尔值，已加引号 `"yes"`/`"no"` 强制为字符串键（JAR
内抽取校验通过，无布尔泄漏）。

**范围外（未迁移，保持现状）**：

- 控制台日志（`consoleError`/`consoleWarn`/`consolePrint`）中的中文 -- 项目既有约定（控制台诊断不走 i18n 键，审计第九节确认"
  全走 consolePrint"）。
- TrMenu 菜单 lore（`skyblockmenu/Permissions/*.yml` 硬编码 `&a岛主`）-- 受 #19 版本提取问题阻塞，属另一条迁移路径。
- `SkyblockExpansion` 中 `&f-` 占位符 -- 非 #23 列项，保留以降低改动面。

**行为说明**：

- `IslandCreateTask`/`IslandDeleteTask` 改走 `MessageUtil.send` 后响应 `-s` 静默标志（#23 预期修复，原代码绕过静默）。
- `IslandCreateTask` 失败分支信息行保留红色（新增 `success-time-error`/`success-id-error`/`success-position-error`
  红色变体键，忠实保留原 §c 颜色）。
- `SkyblockExpansion` `getPlayerRole`/`getPlayerOwnRole` 的异常回退统一为无颜色访客名（原"未找到岛屿"无色、"异常"
  有色，现统一无色，仅异常路径色前缀差异）。

### #24 SkyblockExpansion 去重

新增 4 个 helper 塌缩 7 个重复方法（原 ~133 行 -> ~50 行 + ~40 行 helper）：

- `findIslandAt(chunkX, chunkZ)` -- 提取 `getIslandAt -> ifEmpty getIslandAtMaxRange` 双重查找
- `withIslandAt(chunkX, chunkZ, fallback, extractor)` -- 按位置查找 + 异常兜底 + 提取
- `withOwnIsland(uuid, fallback, extractor)` -- 按玩家查找 + 异常兜底 + 提取
- `formatRole(island, uuid)` + `visitorRoleDisplay()` -- 提取成员->合作者->访客身份显示逻辑

被塌缩的方法：`getIslandName`/`getPlayerRole`/`getIslandLevelHere`/`getIslandValueHere`/`getGeneratorLevelHere`/
`getPlayerOwnIslandName`/`getPlayerOwnRole`，各缩至 3-6 行。原 7 处重复 `consoleWarn` 合并进 2 个 helper。

### 验证

- `./gradlew build`（含 `--no-build-cache --rerun-tasks`）通过，无新增编译错误/警告。
- 7 文件 grep 复查：玩家面向字符串字面量内无残留中文（仅余注释与控制台日志）。
- zh_CN.yml 经 PyYAML 语法校验，23 个顶层键齐全，`yes`/`no` 为字符串键。
- JAR 内 `messages/zh_CN.yml` 抽取校验 `placeholder.yes`/`no` 正确。

---

## 十四、MEDIUM 批量实施（2026-07-10）

执行 7 项 MEDIUM（🟡）建议。经 4 路并行代码核查后，**6 项实施、1 项经核查为误报跳过**。全部通过 `./gradlew build`（含
`--no-build-cache --rerun-tasks` 强制重编译），无新增编译错误/警告（唯一警告为既有的 SchematicManager `findByFile`
deprecation，与本次无关）。不触及被冻结的 DB 代码（SQLiteManager/Repository/LevelManager DB 写）。

### #16 reload 非原子屏障 -- 跳过（误报）

核查结论：`/isadmin reload` 与所有权限/设置事件监听器同在 Bukkit 主线程串行执行，reload 期间不会有事件处理器插入；
`BasePermissionManager.check()` 三分支（publicArea/lockedArea/island）互斥，单次检查不会混合新旧值；原文对数据流的描述有误（
`permissionConfigManager` 只在 publicArea/lockedArea 的 `loadConfig()` 中被读以重填
EnumMap，热路径不直接碰它）。混合配置窗口在当前单线程模型下不可达。`volatile reloading`
屏障在当前模型下是无意义复杂化，仅当未来引入异步权限检查时才有意义。保持现状。

### #31 + #32（合并）Base 类注入 `plugin` + `worldManager`

两目标一致（给 Base 类补注入依赖）、改造点重叠，合并为一次签名变更。`BasePermissionManager`/`BaseSettingManager` 构造器 4
参 -> 6 参，新增 `protected final JavaPlugin plugin` + `protected final SkyblockWorldManager worldManager`；消除两 Base 类共
8 处 `StarMSkyblock.getInstance().getWorldManager()` 热路径调用（改用注入字段），移除 `StarMSkyblock` import。**18 个子类统一
6 参构造器**（12 权限 + 6 设置，机械改动）。`DropPickupPermissionManager` 去特殊化：删除自有 `plugin` 字段与赋值，
`runTask(plugin,...)` 改用继承字段，构造器回归统一签名。`ToolPermissionManager` 的
`JavaPlugin.getProvidingPlugin(getClass())` 改用继承 `plugin`，删除 `Plugin` import。两协调器 `IslandPermissionManager`/
`IslandSettingManager` 补传 `worldManager` 给子类；`StarMSkyblock.initPermissions()`/`registerListeners()` 构造两协调器时补传
`worldManager`。**探查时发现并补改 `ObsidianToLavaListener`**（也是 `BasePermissionManager` 子类，位于 `listener/` 而非
`permission/manager/`，首轮脚本遗漏，已补全 + 更新其 StarMSkyblock 构造点）。改动用 perl 脚本批量处理 20 文件 + 手工补 Base
两类/StarMSkyblock/ObsidianToLavaListener。收益：热路径解耦全局单例、两 Base 类可单测、DropPickup 构造器统一可列表化注册。

### #28 grid-key 去重 + getIslandAt/getIslandAtMaxRange 合并

新建 `island/GridKeys.java`（`static long encode(int x, int z)`，与 IslandManager/IslandDeleteTask 同包无需 import）。替换 *
*6 处**手写 `(((long)x)<<32)|(z&0xffffffffL)` 编码（核查修正：原 #28 称 4 处，实为 6 处 -- IslandManager `addToGridIndex`/
`removeFromGridIndex`/`getIslandAt`/`getIslandAtMaxRange` + IslandDeleteTask 2 处）。合并 `getIslandAt`/
`getIslandAtMaxRange` 为 `findIslandInGrid(chunkX, chunkZ, boolean maxRange)`（两法仅边界谓词 `isChunkWithinIsland` vs
`isChunkWithinMaxRange` 不同），公开方法缩为 1 行委托。**`BlockBreakTaskListener.chunkKey` 不纳入**（核查修正：它用 3 字段
`worldIndex<<32|cx<<16|cz` + `0xFFFF` 掩码的不同编码、不同语义，合并会引入 bug，保留原样）。

### #26 重写 getIslandByPlayerName（消除 deprecated 阻塞）

`IslandManager.getIslandByPlayerName` 改用 `Bukkit.getPlayerExact(name)`（在线，O(1)）+ `playerRepo.getUUID(name)`（启动预热缓存，无
Mojang 阻塞）+ `getIsland(uuid)`；移除 `@SuppressWarnings("deprecation")`。未知玩家返回 `Optional.empty()` 而非触发阻塞的
`Bukkit.getOfflinePlayer(String)` Mojang 请求。未改 DB 代码（复用既有 `PlayerRepository.getUUID`）。**核查发现该方法零内部调用者（死代码）
**，保留方法供潜在外部 API。

### #27 TaskManager 奖励发放去重

新增 `private void grantRewards(UUID uuid, TaskReward rewards)`，自检在线态（`Bukkit.getPlayer(uuid) != null`
），负责金币+物品+命令三块。`claimReward`/`giveForceCompleteRewards` 删除各自重复的奖励块改调
helper；状态变更（setClaimed/clear/markDirty/setNotified/savePlayerProgress/completedTaskIds）、消息
key、返回值、空奖励早退逻辑均留原处。核查确认两路径差异（`Player` vs `OfflinePlayer` 重载、`if(online)` 守卫、名字源）全归结为"
是否在线"一根因，helper 自检即覆盖，**无需 `requireOnline` 参数**。`giveForceCompleteRewards` 的玩家解析块简化为
`Player player = Bukkit.getPlayer(uuid); boolean online = player != null;`（`playerName` 不再外层需要，由 helper 内部算）。删除
`OfflinePlayer` import（不再显式引用该类型）。对两调用方行为逐位等价。`econ.depositPlayer` 由 2 处降为 1 处（仅在 helper）。

### #33 PortalListener 坐标去重

新增 `getIslandCenterBlockXZ(Island)`（返回 `double[]`，`centerChunk*16.0+8.0`）+
`scalePortalLocation(from, targetWorld, island, double factor)`（下界 `1.0/8.0`、主世界 `8.0`）。
`calculateNetherPortalLocation`/`calculateOverworldPortalLocation` 缩为 1 行委托（保留方法名以使调用点自文档化，优于裸
factor 字面量）。`getIslandLocation` 改用 `getIslandCenterBlockXZ` 复用中心块计算（消除 `int startX = centerChunkX*16`
重复）。数学等价性已论证：`offsetX * (1.0/8.0)` 与原 `offsetX / 8.0` 对 2 的幂缩放 IEEE 754 逐位相同。`getIslandLocation`
本质不同（固定出生点、Y/yaw/pitch 来自配置、不缩放 from 偏移），仅共享中心块计算，未并入 `scalePortalLocation`。

### 验证

- `./gradlew build --no-build-cache --rerun-tasks` 通过，无新增编译错误/警告。
- grep 复查：两 Base 类无残留 `getInstance().getWorldManager()`；IslandManager/IslandDeleteTask 无残留手写 `0xffffffffL`
  （BlockBreakTaskListener 的 `0xFFFF` 保留）；ToolPermissionManager 无 `getProvidingPlugin`；DropPickup 无自有 `plugin`
  字段；TaskManager `econ.depositPlayer` 仅 1 处。

### 探查中发现的相关项（未在本次范围，供后续决策）

- `SkullManager:251` 仍有一处 `Bukkit.getOfflinePlayer(playerName)`（String 重载，deprecated 阻塞调用）。属 #20
  范畴（SkullManager 已加 LRU + DB 回退，多数情况不触发 Mojang），但该调用点本身未在 #26 列项中。是否一并修复由用户决定。
- 原 #28 统计为 4 处 grid-key，实为 6 处；`BlockBreakTaskListener.chunkKey` 为不同编码不应合并 -- 已在实施中修正。

---

## 十五、#8 实施（2026-07-10，paper-api 直接调用）

#8（等级扫描区块异步加载）此前因编译依赖为 `spigot-api`（无 `getChunkAtAsync`，见十二(更新)节）而回退。现 `build.gradle:29`
已切到 `io.papermc.paper:paper-api:26.1.2.build.70-stable`，`getChunkAtAsync` 可直接调用、无需反射。本轮用 paper-api 原生
API 实施，`./gradlew build` 通过。

### API 选择

- 用 `World.getChunkAtAsync(int x, int z, boolean gen)`（返回 `CompletableFuture<Chunk>`），**非 deprecated**。
- deprecated 的是 `getChunkAtAsync(int, int, World.ChunkLoadCallback)`（since 1.13.1）--传入 lambda 会被解析到此重载并触发
  deprecation 警告。本轮初版曾误用，已改 future 版消除。
- 经 Context7 查 Paper 26.1.2 javadoc 确认：future "will always be executed synchronously on the main Server Thread"。即
  future 完成在主线程 -> `.whenComplete` 回调在主线程 -> `getChunkSnapshot` 线程安全。

### 改动（`level/IslandLevelCalculator.java`）

- `loadingPhase` 重写：`chunkQueue` 空 -> 转 WAITING；展开 `(ChunkPos, World)` 跳过 `!isChunkGenerated` 的；对每位置发起
  `getChunkAtAsync(x, z, true).whenComplete(...)`，回调（主线程）取快照入批局部 `snapshotTasks`、失败记 `consoleWarn`、
  `finally` 递减 `pendingChunkLoads`；批全部回调完成（`batchRemaining` 归零）后提交异步 `processSnapshots`（复用原
  `runTaskAsynchronously` 路径）。
- 新增 `pendingChunkLoads`（AtomicInteger）跟踪飞行中的 future 数。
- `waitingPhase` 条件改为 `pendingChunkLoads==0 && pendingAsyncTasks==0`（原仅等 `pendingAsyncTasks`）。

### 关键健壮改进

用 `.whenComplete` 而非 `.thenAccept` / Consumer 回调：future 异常完成（区块加载失败）时 `whenComplete` 仍触发、`finally`
仍递减计数。若用 `thenAccept` 或 deprecated `ChunkLoadCallback`，加载失败不触发回调 -> `pendingChunkLoads` 不归零 ->
`waitingPhase` 永久等待 -> 等级计算卡死。此为 Consumer 版的潜在死锁，future + whenComplete 一并修复。

### 验证

- `./gradlew build` 通过，产出单一 `StarMSkyblock-1.0.0.jar`。
- `getChunkAtAsync` deprecation 警告消除（总警告 46 -> 45，其余为既有 deprecation；`IslandLevelCalculator` 零警告）。
- 线程安全：`snapshotTasks`/`batchRemaining` 仅在主线程回调访问（无并发）；提交 `runTaskAsynchronously` 时 scheduler 建立
  happens-before，异步 `processSnapshots` 可见全部快照（与原实现一致）。
- 死常量 `LevelManager.CHUNKS_PER_TICK` 仍未清理（归 #35），本轮不动以保持改动聚焦。

### 运行时待办

在 Paper 26.1.2 服务器跑 `/is level`（大半径岛屿），确认：区块异步加载、主线程不再因同步 `getChunkAt`
卡顿、扫描结果数值正确。若控制台出现"等级扫描异步加载区块失败"日志，排查对应区块加载问题（非致命，该区块跳过、不影响整体计算）。

---

## 十六、补充审计新发现（2026-07-10）

> 在 #8 实施过程中，对监听器/权限/设置、任务系统/异步集成/岛屿生命周期、消息系统做了补充审计（并行 subagent 审计 +
> 自查核实）。以下为此前未覆盖、或对已有项的新补充。编号接续 #44。标注【已核实】者为已读代码确认；其余为审计报告，实施前应复核。命令/配置/主类层（agent
> C）结果待补。
> 严重度：🔴 HIGH · 🟡 MEDIUM · 🟢 LOW

### 性能

**#45** 🔴 `location.getChunk()` 主线程同步生成区块【已核实】
位置：`IslandBoundaryListener.java:155-156`、`PortalListener.java:100/184/435`、`OtherPermissionManager.java:248`（共 9 处，3
文件）
问题：`location.getChunk().getX()` 调 `World.getChunkAt`，区块未驻留时主线程同步加载/生成。
`IslandBoundaryListener.isOutsideIslandArea` 由 `BlockFromToEvent`（液体流动）等 8 个处理器调用，极高频。
`CobblestoneGeneratorListener`/`BorderListener`/`BasePermissionManager` 已正确用 `>>4`，这 3 文件是遗漏。
修复：全替换为 `location.getBlockX() >> 4` / `getBlockZ() >> 4`（位运算，永不加载区块）。

**#46** 🔴 `ToolPermissionManager` 每次右键栅栏 `getNearbyEntities`
位置：`ToolPermissionManager.java:108`（`isPlayerLeadingMob` :714，调用在 `item==null` 检查 :118 之前）
问题：每次右键可拴绳方块（含空手）都 `player.getNearbyEntities(16,16,16)`（遍历 33³ AABB 实体）。mob 农场密集时每次交互开销显著。
修复：移到 `item!=null && item.getType()==LEAD` 之后，或用 `player.isLeashed()`/per-player 标记。

**#47** 🟡 `MoneyTaskListener.getBalance` 主线程阻塞（#38 新角度）
位置：`MoneyTaskListener.java:56`（`runTaskTimer`，主线程）
问题：#38 已核查"无 EARN_MONEY 仍轮询"不成立，但 `getBalance` 在主线程同步执行未覆盖。MySQL/HTTP 支撑的 Vault 经济后端每次
`getBalance` 为阻塞 I/O；N 在线玩家 = 每 10s N 次主线程阻塞查询。
修复：改 `runTaskTimerAsynchronously` + try/catch。

**#48** 🟡 非 FAWE 删除岛屿冻结主线程
位置：`IslandDeleteTask.java:96-109,153-170`
问题：非 FAWE 时 `clearTask`+`finalizeTask` 同主线程 `BukkitRunnable`：标准 WE `EditSession.setBlocks` 同步 +
`world.getNearbyEntities(box)` 强制加载三世界包围盒 + 逐实体 `remove`。大岛屿可卡数百 ms。
修复：非 FAWE 路径按 chunk 分多 tick 清理，或避免 `getNearbyEntities`（另行追踪实体）。

**#49** 🟡 颜色管线无缓存，每条消息跑全套手工扫描
位置：`message/color/ColorUtils.java:166`（`toColor`）；`MessageUtil.send` -> `colorize`
问题：每条消息（聊天/拒绝/PAPI/actionbar）都执行：`translateAlternateColorCodes` + 4 个 `FunctionalColor`（手工 `indexOf`/
`regionMatches` 标签提取）+ `<previousColor>` while 循环 + `getHexadecimalColors` 全串扫描 + 逐个 `replace`。i18n
消息键模板静态、颜色化结果确定可缓存。
修复：`LanguageManager` 层缓存"已颜色化模板"，发送时仅替换 `{占位符}`（需调整 colorize/format 顺序）；`ColorUtils.getColor`（:
266,270）stream 查 16 项改反向索引。
注：颜色引擎用手工 `indexOf`/`regionMatches` 而非 regex，**无 ReDoS 风险**（这点良好）。

**#50** 🟢 多个监听器缺 `ignoreCancelled=true`
位置：`EndProtectionListener.java:33,48`；`BlockPlaceListener.java:39`；`ItemPermissionManager.java:60`
问题：已取消事件仍跑全量解析；`ItemPermissionManager.onItemUse` 还可能双发拒绝消息（其他插件 LOW/NORMAL 取消后仍解析）。
修复：加 `ignoreCancelled=true`（与其他权限管理器一致）。

**#51** 🟢 `ContainerPermissionManager` 冗余 `block.getState()`
位置：`ContainerPermissionManager.java:375`（`getContainerPermission`），:114,130
问题：对每个右键方块都 `block.getState()` 创建快照（含非容器方块如泥土石头）；JUKEBOX/WOODEN_SHELVES 后续再 `getState()` 一次。
修复：先按 `material` 枚举短路；已知容器材质 `getState()` 一次复用。

**#52** 🟢 `Island.getMembers()` 每次全拷贝
位置：`Island.java:459-461`
问题：`return new HashMap<>(members)` 每次拷贝整表。`notifyIslandMembers`（`PortalListener:162`）仅需 `.keySet()`。
修复：加 `getMemberUuids()` 返回 `Collections.unmodifiableSet(members.keySet())`。

**#53** 🟢 `WorldBorderReflection.init()` 每次 `synchronized`
位置：`WorldBorderReflection.java:134`
问题：每次 `sendWorldBorder`/`resetWorldBorder` 都 `synchronized`（init 内首行 `if(initialized) return`）。虽主线程无竞争但锁开销仍在。
修复：`initialized`/`available` 改 `volatile` + 双检锁。

### 健壮性

**#54** 🔴 `LevelManager` 技能加成 future 无 `exceptionally()`【已核实】
位置：`LevelManager.java:143`
问题：`futureResult.thenAccept(...)` 无 `exceptionally`/`whenComplete`。AuraSkills/mcMMO 异步查询失败时 `thenAccept`
跳过 -> 玩家收不到结果消息 + DB 不落库（:161 `updateLevel` 在 `thenAccept` 内），而内存 `island.setLevel(blockLevel)`（:
124）已改 -> 内存与 DB 不一致，重启后等级回退。
修复：链加 `.exceptionally(e -> { 记日志; 回退方块等级落库 + sendLevelResults; return null; })`。
注：与 #30（异常静默吞没）同类但不同位置，#30 未列此项。

**#55** 🔴 `IslandCreateTask` 粘贴失败无回滚【已核实】
位置：`IslandCreateTask.java:62`（`createIsland` 入库入索引）-> :88 `completeCreation`
问题：`createIsland` 已插入 DB 行 + 更新 6 个内存索引；若 `completeCreation` 在世界加载/偏移逻辑处抛异常（:70-73 仅记日志 +
发 error key），岛屿留在 DB+内存但无 schematic，玩家无法再次 `/is create`（`islandsByOwner` 含该玩家）。
修复：`completeCreation` 失败时调 `islandManager.deleteIsland(createdIsland)` 回滚（DB+内存）。

**#56** 🔴 `saveAllDirty` 脏标记提前清除致静默丢进度【已核实】
位置：`TaskManager.java:224`（在 :227 `batchSaveTasks` 之前 `dirtyPlayers.remove`）
问题：循环内先 `remove` 脏标记再序列化，`batchSaveTasks` 在循环后执行且内部捕获 `SQLException` 仅记日志。若
`batchSaveTasks` 失败，脏标记已清 -> 自上次成功保存以来的进度永久丢失，无重试。`savePlayerProgress`（:212）同病。
修复：把 `dirtyPlayers.remove` 移到 `batchSaveTasks` 成功返回后，或失败时重新标记。

**#57** 🔴 `deleteIslandFromDatabase` 无条件 `return true`【已核实】
位置：`IslandManager.java:722`（`return true`）；`IslandRepository.java:468`（`deleteIslandCascade` 吞 `SQLException` 仅记日志）
问题：DB 删失败时 `deleteIslandFromDatabase` 仍 `return true`，`IslandDeleteTask.finalizeDeletion` 据此
`incrementDeleteCount`（玩家损失删除额度），而 `DeleteCommand` 已 `removeIslandFromMemory` -> 内存清、DB 行在 -> 重启后幽灵岛屿复活。
修复：`deleteIslandCascade` 改返回 boolean 或重抛；`finalizeDeletion` 据真实结果决定是否计次/通知。注：`deleteIslandCascade`
属冻结 DB 代码，`IslandManager` 包装层与计次逻辑可改。

**#58** 🟡 AuraSkills/mcMMO 异步线程调 `Bukkit.getOfflinePlayer`（#2 修复的回归）
位置：`AuraSkillsIntegration.java:106`；`McMMOIntegration.java:119`
问题：#2 把 mcMMO 查询移到 `supplyAsync`（ForkJoinPool）是对的，但 `getOfflinePlayerName` 跟着进异步线程调
`Bukkit.getOfflinePlayer(uuid).getName()`，该 API 非线程安全、未缓存玩家时可能阻塞发 Mojang 请求。AuraSkills 同病。
修复：主线程采集成员名字传入，或用已预热的 `playerRepo.getPlayerName(uuid)`。

**#59** 🟡 `evictStaleProgress` TOCTOU 竞态
位置：`TaskManager.java:149-158`
问题：异步线程检查 `Bukkit.getPlayer(uuid)==null` 后 `savePlayerProgress` + `remove`；期间玩家上线 `onJoin` ->
`loadPlayerProgress` 塞入新进度，异步线程随后 `remove` 掉刚加载的进度，丢失突变。
修复：`remove` 前再检 `Bukkit.getPlayer(uuid)!=null`，或改主线程执行淘汰。

**#60** 🟡 `ItemPermissionManager.onPlayerInteractEntity` 缺 hand null 检查
位置：`ItemPermissionManager.java:152-156`
问题：唯一不先 `event.getHand()!=EquipmentSlot.HAND` 的 `PlayerInteractEntityEvent` 处理器（Entity/Container/Tool/Other
都有），`getItem(null)` 在 Paper 上 NPE。
修复：加 `if (event.getHand() != EquipmentSlot.HAND) return;`。

**#61** 🟡 `PortalListener.handleEndPortal` cancel+setTo+teleport 三者并施
位置：`PortalListener.java:361-363`
问题：取消的事件 `setTo` 无效；手动 `teleport` 与其他传送插件冲突可能双传/世界加载竞态。
修复：二选一--`setTo` 不取消（让 Bukkit 传送），或取消+手动 `teleport` 不 `setTo`。

**#62** 🟡 `PortalListener` 延迟传送覆盖他插件有意传送
位置：`PortalListener.java:411-419`
问题：传送后下 tick 检查仍否在目标世界，否则强制再传。若其他插件（spawn/teleport 插件）有意移走玩家，会被拽回。
修复：移除延迟传送，或加标志跳过被其他来源传送的玩家。

**#63** 🟡 `IslandLevelCalculator` 无同岛并发计算保护
位置：`LevelManager.java:74`（`cooldownSeconds=0` 时）
问题：cooldown=0（配置允许禁用冷却）时同一玩家可并发跑两个 calculator，都写 `rawTotalCounts`（虽有 `synchronized`）但都
`onComplete` -> 双重 `updateLevel` + 双消息。
修复：加 `Set<UUID> calculating` 防重入守卫。

**#64** 🟡 `PortalListener.lastPortalEnter` 非玩家实体 UUID 泄漏（#21 补充）
位置：`PortalListener.java:49,59-62,387-390`
问题：#21 已加 `PlayerQuitEvent` 淘汰玩家 UUID，但 `onEntityPortalEnter` 仍对所有实体（含 mob）`put`，`PlayerQuitEvent` 不清
mob UUID -> 非玩家 UUID 无界累积。
修复：`onEntityPortalEnter` 只跟踪玩家，或用 `EntityRemoveFromWorldEvent` 清理。

**#65** 🟢 `loadPlayerProgress` 存入 null `TaskProgress`
位置：`TaskManager.java:185`
问题：`if(tp!=null)` 只保护 `ensureConcurrent`，`put` 仍放进 null；PAPI/命令迭代时 NPE。
修复：`if (tp == null) continue;` 在 `put` 前。

**#66** 🟢 `TaskProgress` 非 volatile 字段被异步 Gson 读（#13 同类）
位置：`TaskProgress.java:19-31`；`TaskManager.java:210,222`
问题：`claimed`/`notified`/`completedCount` 非 volatile，主线程写、异步 `saveAllDirty` 的 `gson.toJson` 反射读无
happens-before，可能捕获不一致快照。内层 progress map 已是 `ConcurrentHashMap`（安全），标量字段不是。
修复：标量字段改 `volatile`，或保存时主线程快照。

**#67** 🟢 `createIsland` DB+索引非原子
位置：`IslandManager.java:237-248`
问题：`insertIsland` 后逐个更新 6 索引；期间他线程 `getIslandAt` 可能见 `islandsById` 有但 grid 索引无。per-owner
锁防同玩家并发但不防他线程读。窗口微秒级，影响低。
修复：可接受现状；或先全建内存索引再统一发布。

**#68** 🟢 `ColorUtils.getSection` 串首字符 `charAt(-1)` 越界
位置：`TagContentExtractor.java:265,308`（`getSection`/`getSections`）
问题：目标字符在串首时 `startPos=0`，`charAt(startPos-1)=charAt(-1)` 抛 `StringIndexOutOfBoundsException`。被
`ColorUtils.toColor` 的 try/catch 兜住（仅该条不着色，不崩）。
修复：`getSection`/`getSections` 加 `startPos==0` 边界处理。

**#69** 🟢 `cooldowns` 无淘汰（#6 剩余）
位置：`LevelManager.java:56`
问题：#6 已改 `ConcurrentHashMap`（线程安全部分），但"时间淘汰"未实施，条目随独立玩家数无界增长。
修复：`put` 时顺手清理 `now-value > 2*cooldownMs` 的条目，或定时清理。

**#70** 🟢 `Island` 内部 HashMap/HashSet 非线程安全
位置：`Island.java:151,161,165,169,176`
问题：`IslandManager` 用 `ConcurrentHashMap` 索引，`Island` 内部用 `HashMap`/`HashSet`/`EnumMap`。当前突变全在主线程（命令）故无活跃竞态；
`IslandDeleteTask.run` 异步读 `island.getRadius()` 等为 int（原子，安全）。若未来异步任务突变 member/coop 状态，HashMap
扩容可能死循环/丢更新。混合模式脆弱。
修复：`Island` 可变集合改 `ConcurrentHashMap`/`newKeySet`，或加 `@MainThread` 契约。

### 功能

**#71** 🟡 `CraftingTaskListener` shift-craft 少算约 98%【已核实】
位置：`CraftingTaskListener.java:39`
问题：用 `result.getAmount()`。shift-click 合成时 `InventoryClickEvent` 只触发一次、`getCurrentItem().getAmount()`
返回单次配方产量（常 1），非实际合成数。shift 合成 64 个仅计 1，CRAFTING 任务经 shift-craft 几乎不可完成。
修复：改用 `CraftItemEvent`，按移动量/配方结果计算实际合成数。

**#72** 🟡 `OtherPermissionManager.onPlayerPortal` 绕过 locked-area 权限覆盖
位置：`OtherPermissionManager.java:269`
问题：直接调 `island.hasPermission(...)` 而非 `resolve()`/`check()` 流程。标准流程在玩家处于 locked-area（已解锁半径外、最大半径内）时用
`lockedAreaConfig` 覆盖；此处绕过 -> locked-area 内玩家拿到岛屿自身权限而非锁定区配置。代码注释（:271）已意识到绕过但未修正。
修复：改用 `checkPermission(from, player, permission)` 走标准流程。

**#73** 🟡 `ExplosionSettingManager` 只查爆炸源位置不查受影响方块
位置：`ExplosionSettingManager.java:61-79`
问题：检查 `event.getLocation()`（爆炸实体位置）。TNT/苦力怕站在岛屿边界外的公共区（允许爆炸）引爆，仍摧毁禁爆岛屿边界内的方块。
修复：遍历 `event.blockList()`，移除 `checkSetting` 为 false 的方块（部分爆炸，保护区方块保留）。

**#74** 🟢 `BlockBreakTaskListener` chunk-unload 丢 only-natural 跟踪
位置：`BlockBreakTaskListener.java:75-78`
问题：`onChunkUnload` 移除整 chunk 的 placed-block 集合；区块重载后已跟踪的玩家放置方块被视为自然生成，only-natural
`BLOCK_BREAK` 任务误计。空岛区块少卸载，影响有限。
修复：持久化 placed-block 标记，或接受现状（边缘场景）。

### 优先级建议（新增项）

- 第一优先（数据/正确性）：#56 静默丢进度、#57 幽灵岛屿、#54 技能加成不落库、#55 创建失败留孤儿、#71 合成少算、#72/#73 权限/爆炸绕过
- 第二优先（主线程卡顿）：#45 `getChunk()`、#46 `getNearbyEntities`、#47 `getBalance` 主线程、#48 删除冻结
- 其余 MEDIUM/LOW 按需

### 与冻结/已有项关系

- 冻结 DB 代码相关：#57（`deleteIslandCascade` 属冻结，包装层可改）、#47/#48 涉及 DB 邻接
- 已有项补充：#64 补充 #21、#69 补充 #6 剩余、#47 补充 #38 新角度、#66 同类 #13
- 命令/配置/主类层（agent C）结果待补

---

## 十七、正确性+性能批量实施（2026-07-10）

执行第一/二优先中的 5 项非冻结建议（#45/#55/#71/#72/#73），全部通过 `./gradlew build --no-build-cache --rerun-tasks`
编译验证（45 个既有 deprecation 警告，无新增）。不触及冻结 DB 代码（SQLiteManager/Repository/LevelManager DB 写）。

### #45 `location.getChunk()` 同步区块加载 -> `>>4` 位运算【已核实+实施】

位置：`IslandBoundaryListener.java:155-156`、`PortalListener.java:100-101/184-185/435-436`、
`OtherPermissionManager.java:248-249`
改动：5 处 `location.getChunk().getX()/getZ()` 改 `getBlockX()>>4` / `getBlockZ()>>4`（数学等价：Bukkit 内部即 `blockX>>4`
，但避免区块未驻留时主线程同步加载）。`IslandBoundaryListener`（由 `BlockFromToEvent` 等 8 个处理器经 `isOutsideIslandArea`
高频调用）与 `PortalListener` 3 处均已修；`OtherPermissionManager:248` 随 #72 重写一并消除。`CobblestoneGeneratorListener`/
`BorderListener`/`BasePermissionManager` 早用 `>>4`，本次补齐遗漏的 3 文件。

### #55 IslandCreateTask 创建失败回滚孤儿岛屿【已核实+实施】

位置：`IslandCreateTask.java` `run()` 内 `completeCreation()` catch（原 :70-73）
改动：catch 中调 `islandManager.deleteIsland(createdIsland)` 回滚已写入的 DB 行 + 6 内存索引，使玩家可重新 `/is create`。
`deleteIsland` 仅做 DB（`islandRepo.deleteIslandCascade`，调用既有冻结方法未改）+ `cleanupIndices`，不触碰世界方块（清方块是
`IslandDeleteTask` 职责），适合回滚。仅覆盖 `completeCreation` 直接抛异常的早期失败（世界加载/坐标计算）；`pasteSchematicSync`
自捕获异常返回 false、`scheduleNext` 链与 `finishCreation` 各自有独立 catch，属部分成功场景不回滚。回滚本身再 try/catch
防二次异常吞掉原错误。`createdIsland` 由 volatile + `runTask` happens-before 保证 catch 处非空。

### #71 CraftingTaskListener shift-click 合成少算~98%【已核实+实施】

位置：`task/listener/CraftingTaskListener.java`
改动：`InventoryClickEvent` 改 `CraftItemEvent`（仅合成结果点击触发，无需手动 slot/type 过滤）。原
`getCurrentItem().getAmount()` 在 shift-click 下为单次配方产量（常 1）。新 `computeCraftAmount`：非 shift 返回单产；shift 取
`min(maxCraftableFromMatrix, operationsBySpace) × 单产`——矩阵次数取非空原料格堆叠最小值（标准配方每次各消耗 1），背包次数按
`getStorageContents()` 中空格/同类格剩余容量除以单产。返回容器类配方（如空桶）按全消耗估算（与原实现一致）。`amount>0` 守卫防计
0。

### #72 OtherPermissionManager 传送门权限绕过 locked-area【已核实+实施】

位置：`permission/manager/OtherPermissionManager.java` `onPlayerPortal`
改动：原手动 `getIslandAt -> getIslandAtMaxRange -> getIslandByPlayer` 三段回退 + 直接 `island.hasPermission(...)`（绕过
locked-area 覆盖）。改为 `enforce(event, from, player, permission)` 走标准 `resolve` 流程：locked-area 用
`lockedAreaConfig`、公共区域用 `publicAreaConfig`、bypass/非空岛世界/null world 由 resolve/check 统一处理。顺带：消除
`from.getChunk()` 同步加载（resolve 用 `>>4`，覆盖 #45 该站点）；拒绝消息现据 resolve 结果正确显示
locked-area/public-area/no-permission（原代码 `lastCheckResult.remove` 强制走默认 no-permission）。**行为变更**：去掉
`getIslandByPlayer` 回退——传送门位置不在任何岛屿时，由"查玩家自身岛屿权限"改为"查公共区域配置"，与位置化权限模型更一致（spawn
传送门归 spawn 配置管辖，而非玩家岛屿设置）。删除随之失效的 `World`/`Optional`/`Island` 三个 import。

### #73 ExplosionSettingManager 跨边界爆炸破坏受保护方块【已核实+实施】

位置：`setting/manager/ExplosionSettingManager.java` `onEntityExplode`
改动：原仅查 `event.getLocation()`（爆炸源）决定整事件取消，跨边界进入禁爆岛屿的爆炸仍摧毁受保护方块。新逻辑：按实体映射
`IslandSetting`（不支持来源 `null` 直接返回）；源位置禁该类爆炸 -> `setCancelled(true)`
整事件取消（保留原保守行为）；源位置允许 -> `event.blockList().removeIf(b -> !checkSetting(b.getLocation(), setting))`
按每个受影响方块自身位置的设置过滤，移除禁爆区域方块（部分爆炸，保护区方块保留）。`checkSetting` 内部 `>>4` + 尊重
public/locked-area 覆盖，无同步区块加载；爆炸事件非超高频，逐块 O(1) 查找可接受。

### 验证

- `./gradlew build --no-build-cache --rerun-tasks` 通过，45 个既有 deprecation 警告（`RainbowColor`/`MessageUtil`/
  `SignSide`/`SchematicManager`），**本次 5 文件零新增警告**。
- 产出单一 `build/libs/StarMSkyblock-1.0.0.jar`（#40 配置）。
- grep 复查：`listener/`+`permission/manager/` 下无残留 `getChunk()`；`OtherPermissionManager` 无残留 `World`/`Optional`/
  `Island` import、无 `hasPermission` 直调、无 `lastCheckResult.remove`。

### 推迟（待决策）

- **#54**（LevelManager 技能加成 future 无 `exceptionally`，失败致内存/DB 等级分叉）：属 LevelManager（DB 冻结区），加
  `.exceptionally()` 调既有 `updateLevel` 做 block-level 回退落库。需确认是否解冻。
- **#56**（TaskManager 脏标记提前清除致静默丢进度）：完整修复需 `PlayerRepository.saveTasks/batchSaveTasks` 失败时回传
  boolean（冻结）。仅 TaskManager 侧重排只能覆盖 RuntimeException 路径，无法探测被吞的 SQLException。需确认是否解冻。
- **#57**（`deleteIslandFromDatabase` 无条件 `return true` 致幽灵岛屿+误计删除额度）：需
  `IslandRepository.deleteIslandCascade` 回传 boolean（冻结），IslandManager 包装层+计次逻辑可随之修正。需确认是否解冻。
- **2026-07-10 复核：用户选择保持 DB 冻结，#54/#56/#57 维持冻结，推进非 DB 项。**（见十七节同条）
- 其余非冻结项（#46 `getNearbyEntities`、#47 MoneyTaskListener 异步、#48 非 FAWE 删除冻结、#50 `ignoreCancelled`、#60 hand
  null 等）可继续按批推进，无需解冻。

---

## 十八、健壮性+性能批量实施（2026-07-10）

执行 3 项非冻结建议（#60/#50/#47），全部通过 `./gradlew build --no-build-cache --rerun-tasks`（45 个既有 deprecation
警告，无新增）。不触及冻结 DB 代码。

### #60 ItemPermissionManager onPlayerInteractEntity hand null NPE【已核实+实施】

位置：`permission/manager/ItemPermissionManager.java` `onPlayerInteractEntity`
改动：方法首加 `if (event.getHand() != EquipmentSlot.HAND) return;`。`PlayerInteractEntityEvent.getHand()` 可为
null（潜行等非手部交互），原 `player.getInventory().getItem(event.getHand())` 传 null 在 Paper 上 NPE。与
Entity/Container/Tool/Other 四个同类监听器一致（它们都先做此守卫）。补 `import org.bukkit.inventory.EquipmentSlot`。

### #50 补 ignoreCancelled=true【已核实+实施】

位置：`EndProtectionListener.java:33/48`、`BlockPlaceListener.java:39`、`ItemPermissionManager.java:60`
改动：4 个处理器加 `ignoreCancelled = true`
。已取消事件跳过全量解析：EndProtection（取消末影龙/末地水晶生成，已取消即目标达成）、BlockPlace（取消+actionbar，避免重复提示）、ItemPermissionManager.onItemUse（避免其他插件
LOW/NORMAL 取消后仍发拒绝消息）。与其他权限管理器一致。

### #47 MoneyTaskListener getBalance 主线程阻塞【已核实+实施】

位置：`task/listener/MoneyTaskListener.java`
改动：`startBalanceCheckTask` 由 `runTaskTimer`（主线程）改 `runTaskTimerAsynchronously`。`getBalance` 可能为阻塞
I/O（MySQL/HTTP 后端 Vault 经济），原每 10s × N 在线玩家阻塞主线程。异步线程读余额+算差值，正增量收集后 `runTask` 切回主线程调
`incrementProgress`（完成消息 `MessageUtil.send` 需主线程）。`lastBalances` `HashMap` -> `ConcurrentHashMap`（异步轮询与主线程
recordBalance/quit 跨线程读写）。`getEconomy` 已解析时为缓存字段读，安全；每轮询周期捕获一次 `Economy` 引用并 null
守卫，避免逐玩家重复解析。**`recordBalance` 保持同步**（join 时单次读取，且 join 即完成写入早于首次轮询 200tick，避免异步
recordBalance 与轮询的陈旧覆写竞态致双计）。

### #58 AuraSkills/mcMMO 异步线程 getOfflinePlayer 回归【已核实+实施】

位置：`integration/McMMOIntegration.java`、`integration/AuraSkillsIntegration.java`
改动：#2 把 mcMMO/AuraSkills 查询移到异步线程（`supplyAsync`/`thenApply`）后，`getOfflinePlayerName(uuid)` 内部
`Bukkit.getOfflinePlayer(uuid).getName()` 也跟着在异步线程执行 -- 该 API 非线程安全、未缓存玩家时可能阻塞发 Mojang
请求。改为在调用线程（主线程）采集成员名：在线用 `getName`、离线用预热的 `playerRepo.getPlayerName(uuid)` 缓存（启动已预热，缓存命中无
DB/无 Mojang 阻塞），异步任务直接用预采集 `names[]`。删除两处 `getOfflinePlayerName`
。在线态仍在异步线程重新判定（保持原实现的当前性），名字采集与异步查询解耦。#2 的"主线程采集成员 UUID 列表"
防跨线程读成员集保留；本轮补齐"主线程采集名字"，#2 异步化收尾。

### #46 核查结论：原报告建议修复有误，暂缓【已核实，未实施】

位置：`permission/manager/ToolPermissionManager.java:107-108`（`isPlayerLeadingMob` :714）
核查：原报告建议"移到 `item!=null && item.getType()==LEAD` 之后"。但 Minecraft 机制中，玩家**牵着**已被拴绳的生物时，可**空手
**右键栅栏将其拴到栅栏上（拴绳已附在生物上，无需手持）。因此 `toolType==LEAD` 守卫会错误跳过空手拴栅栏的合法场景，是行为回归而非优化。
`isPlayerLeadingMob` 内部用 `getNearbyEntities(16,16,16)` 扫描被拴向玩家的生物（无直接 API 查询玩家正在牵引的生物）。正确修复需事件化跟踪牵引状态（
`PlayerLeashEntityEvent` 标记 + `EntityUnleashEvent`/quit 清除 + 按玩家计数，避免单生物 unleash
误清多生物），属较大改动且有计数漂移风险（漂移致假阳性→误拒栅栏交互）。暂缓，待单独评估。影响仅限密集 mob 农场。

### 验证

- `./gradlew build --no-build-cache --rerun-tasks` 通过，45 个既有 deprecation 警告，**本次 6 文件零新增警告**。
- 产出单一 `build/libs/StarMSkyblock-1.0.0.jar`。
- grep 复查：`integration/` 下无 `getOfflinePlayerName`/`Bukkit.getOfflinePlayer` 调用（仅注释提及）。

### 推迟/待续（非冻结，未在本轮）

#48（非 FAWE 删除冻结主线程）、#59（evictStaleProgress TOCTOU）、#61/#62/#64（PortalListener
多项）、#65/#66/#69/#70（各类健壮性）等可后续按批推进。#63 触及 LevelManager（冻结区），暂缓。#49/#68 随颜色代码冻结（见下十九节），不再推进。

---

## 十九、#17 实施 + 颜色代码冻结（2026-07-10）

### #17 level.yml `auraskills:` 段名误导 -> `skill-contribution:`

用户要求直接改名、不考虑迁移（硬切策略 B）。改动：

- `src/main/resources/level.yml:1073` 段键 `auraskills:` -> `skill-contribution:`（默认 `type: mcmmo` 不变；`type` 合法值仍为
  `auraskills`/`mcmmo`）。
- `config/AuraSkillsContributionConfig.java`：`SECTION_NAME = "skill-contribution"`（line 23，line 53/56
  警告经常量自动更新）、javadoc（line 13）、coefficient 警告文案（line 66）。
- `README.md`：第 82 行 `auraskills.type` -> `skill-contribution.type`、第 282 行示例段键、第 334 行章节说明（移除"
  章节名沿用历史命名"的过时括注）。

**未改（有意保留）**：

- 类名 `AuraSkillsContributionConfig` 及 `LevelManager` 中 `auraskillsConfig` 变量名 —— 牵动 LevelManager（DB
  冻结邻接区），且属内部命名非运维面向，#17 的运维误导已由段键改名消除。
- DB 列 `auraskills_contribution`（`SQLiteManager`/`IslandRepository`，冻结）、`Island`/`LevelResults` 的
  `auraskillsContribution` 字段（绑 DB 列）。
- `LevelManager.java:130` `"auraskills".equalsIgnoreCase(skillType)` —— 此处比较的是 `type` **值**（合法枚举
  `auraskills`/`mcmmo`），非段名。
- 历史来源注释 `# 合并自原 ... auraskills-contribution.yml`（旧文件名，忠实记录来源）。

**迁移说明（用户已接受此成本）**：`initialize()` 用 `saveResource(FILE_NAME, false)` 不覆盖已存在文件，故已部署且自定义过
`level.yml` 的服务器升级后 reader 找不到 `skill-contribution:` 段 -> consoleWarn + 回退默认值。需手动将旧 `auraskills:`
键改名为 `skill-contribution:` 或删旧文件重生成。

### 颜色代码冻结

用户追加约束：文字颜色相关代码（`ColorUtils`/`TagContentExtractor`/`FunctionalColor`/`MessageUtil.colorize` 路径）不修改。据此：

- **#49**（颜色管线无缓存）、**#68**（`getSection` `charAt(-1)` 越界）从"可推进"移出，归冻结区，不再推进。
- 与 DB 冻结（`SQLiteManager`/`Repository`/`LevelManager` DB 写）同列，需用户解冻后才能动。

### 验证

- `./gradlew build --no-build-cache --rerun-tasks` 通过，45 个既有 deprecation 警告（无新增）。
- 产出单一 `build/libs/StarMSkyblock-1.0.0.jar`。
- grep 复查：`src/main/` 内无残留 `auraskills:` 段键或 `"auraskills"` 段名读取；新 `skill-contribution:` 键就位。

---

## 二十、#34 实施（2026-07-10）

引入 JUnit 5（Jupiter 5.13.4）+ 把纯逻辑抽成 helper 类并加测试。全部通过 `./gradlew build --no-build-cache --rerun-tasks`
（45 个既有 deprecation 警告，无新增），`./gradlew test` 5 类 40 用例全绿。不触及冻结区（DB 写 / 颜色代码）。

### 测试基础设施（`build.gradle`）

- `testImplementation platform('org.junit:junit-bom:5.13.4')` + `testImplementation 'org.junit.jupiter:junit-jupiter'` +
  `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'`，`test { useJUnitPlatform() }`。
- 纯逻辑测试不经 Bukkit 运行时：helper 全部无 Bukkit 依赖，测试只引用这些 + 项目枚举。`IslandPermissionLevel` 虽 import
  `MessageUtil`，但仅 `getDisplayName`/`fromString` 用到（懒加载），测试不调它们即不触发 Bukkit/Adventure 类加载 —— 实测 8
  用例全过验证。
- JUnit 版本经 Context7 核实（5.13.4 当前稳定版）。

### 抽取的纯 helper（4 个新类，均在 main 源集）

| 类                        | 抽自                                                                                  | 职责                                                                                  |
|--------------------------|-------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| `level/ExpressionParser` | `IslandLevelCalculator` 内 `evaluateSimpleExpression` + `parseAddSub/MulDiv/Primary` | 递归下降算术解析（+-*/、括号、小数）                                                                |
| `level/LevelFormula`     | `IslandLevelCalculator.finishPhase`                                                 | `fromPowerCurve`（幂函数反推等级，含 cost<=0 溢出保护）、`diminishingReturns`（递减收益，即 #11 的 O(n) 循环） |
| `grid/UlamSpiral`        | `GridManager.getChunkLocation`                                                      | Ulam 螺旋 O(1) 坐标偏移                                                                   |
| `util/BlockCoordKeys`    | `BlockBreakTaskListener` 私有静态                                                       | 48-bit chunkKey / 18-bit blockKey 编解码（新增 decode，含负 chunkX/Z 符号扩展）                   |

### 改造的调用点（5 处，行为字节级等价）

- `IslandLevelCalculator.finishPhase`：递减循环 -> `LevelFormula.diminishingReturns`；幂函数 while 循环 ->
  `LevelFormula.fromPowerCurve`；旧公式 -> `ExpressionParser.evaluate`（移除内联解析器 4 方法）。
- `GridManager.getChunkLocation` -> `UlamSpiral.spiralOffset` × cellSize。
- `IslandPermissionLevel` 新增实例方法 `hasPermission(IslandPermission, Integer configuredMinLevel)`（OWNER 全通过 /
  null->false / 否则 level>=min）；`Island.hasPermission(role,perm)` 委托之（顺带修正"回退 ALL 兜底"的过时注释——实际 null
  即拒绝）。
- `BlockBreakTaskListener`：`chunkKey(String,int,int)` / `blockKey(Block)` 委托 `BlockCoordKeys`，保留 `WORLD_INDICES`
  状态分配。**此 48-bit 编码与 #28 `GridKeys`（岛屿网格键）语义不同，不合并**。

### 测试用例（5 类 40 个）

- `UlamSpiralTest`（5）：原点 (0,0)、前 9 格手算、0..1000 比对参考实现、501 格唯一性、相邻格曼哈顿距离 1。
- `ExpressionParserTest`（10）：优先级、左结合、括号嵌套、除法、小数、空白、空串->0、尾随运算符锁定、除零->Infinity。
- `LevelFormulaTest`（9）：低于阈值->0、线性曲线对算术级数、power=0 常数 cost、**负 power 的 cost<=0 守卫终止**、极大 totalExp
  不死循环、递减收益手算 + minimum 下限 + 大 overLimit(1M) 超时保护。
- `IslandPermissionLevelTest`（8）：层级严格、`getManageableRoles` 四档、`hasPermission` OWNER/null/等级比较。
- `BlockCoordKeysTest`（8）：chunk 正/负/边界往返、worldIndex 溢出抛异常、唯一性、block 往返、x/z 越界掩码、y 全范围。

### 未纳入（有意）

- `IslandPermissionLevel.fromString` / `getDisplayName`（依赖 i18n/LanguageManager，非纯）、`Island.hasPermission(UUID,...)`
  （需 Island 实例+成员 Map，集成性）、`GridManager` 构造（依赖 ConfigManager）。
- `LevelManager` 内的等级协调/DB 写（冻结区）—— 等级公式实际在 `IslandLevelCalculator.finishPhase`，不在
  LevelManager，故抽取全程未触冻结。

### 验证

- `./gradlew test`：5 类 40 用例，0 失败 0 错误 0 跳过。
- `./gradlew build --no-build-cache --rerun-tasks` 通过，45 个既有 deprecation 警告（color/MessageUtil/IslandCreateTask），*
  *新增 4 helper + 5 测试零警告**。
- 产出单一 `build/libs/StarMSkyblock-1.0.0.jar`。
- grep 复查：`IslandLevelCalculator` 无残留 `evaluateSimpleExpression` / `parseAddSub` 等；5 处调用点委托到位；
  `Island.java` 委托生效。

### 附带收益

- `#11`（递减收益 O(n) 循环）：抽出 `LevelFormula.diminishingReturns` 可单测，大 overLimit 行为有测试锁定（性能另议，循环本身未改算法）。
- `BlockCoordKeys` 新增 decode（原代码只 encode 做 hash key）；测试驱动确认 ±32767 chunkX/Z 往返正确（负值经符号扩展还原）。

---

## 二十一、#9 #10 #37 #54 #56 #57 #63 实施（2026-07-11）

用户解冻 DB 代码（#3/#7 除外），实施 7 项非 #3/#7 的 DB 相关建议。全部通过 `./gradlew build --no-build-cache --rerun-tasks`
（45 个既有 deprecation 警告，零新增）和 `./gradlew test`（5 类 40 用例全绿）。

### #9 executeInTransaction 只读事务用 writeLock 阻塞所有读【已实施】

**位置**：`database/SQLiteManager.java`

改动：新增 `executeReadTransaction(TransactionCallback<T>)`——语义与 `executeInTransaction` 相同但使用 `readLock`
而非 `writeLock`，避免长时间批量写阻塞简单读。JavaDoc 注明 #3（单 Connection 非线程安全）仍存在，此方法与既有 `readLock`
用法（`loadAllSkinTextures`、各 `PlayerRepository` getter）处于同一风险等级，不引入新风险；#3 解决后自然安全。

### #10 PlayerRepository 未用 prepareCached + batchSaveTasks 关闭缓存语句【已实施】

**位置**：`database/PlayerRepository.java`

改动：
- 全部 11 处 `conn.prepareStatement(sql)` 改为 `sqliteManager.prepareCached(sql)`（消除每次调用的 SQL 解析/规划开销）。
- 删除随之成为死代码的 `Connection conn = sqliteManager.getConnection()` 行。
- **`batchSaveTasks` 关键修复**：移除 `try-with-resources` 包装——原代码用 `try (PreparedStatement pstmt =
  conn.prepareStatement(sql))` 在事务回调内关闭了缓存语句，下次调用 `prepareCached` 检测到 `isClosed()` 需重建，
  缓存形同虚设。改为 `PreparedStatement pstmt = sqliteManager.prepareCached(sql)` 不关闭，生命周期由缓存管理。

### #37 IslandManager 泄漏 IslandRepository 给 LevelManager 直写【已实施】

**位置**：`island/IslandManager.java`、`level/LevelManager.java`

改动：
- `IslandManager` 新增 `updateLevel(id, level, exp, blockCountsJson)` / `updateLevel(..., bonus)` /
  `updateBaseline(id, baselineExp, baselineJson)` 三个 facade 方法——在委托 `islandRepo` 的同时同步更新内存
  `Island` 对象（`setLevel`/`setExperience`/`setAuraSkillsContribution`/`setBaselineExperience`），
  消除内存/DB 分叉风险。
- `LevelManager` 3 处 `islandManager.getIslandRepository().updateLevel/updateBaseline(...)` 全部改为
  `islandManager.updateLevel/updateBaseline(...)`。`getIslandRepository()` 的调用归零。

### #54 LevelManager 技能加成 future 无 exceptionally（内存/DB 等级分叉）【已实施】

**位置**：`level/LevelManager.java` `onCalculationComplete`

改动：在 `futureResult.thenAccept(...)` 后链加 `.exceptionally(e -> {...})`。AuraSkills/mcMMO 异步查询失败时：
1. `MessageUtil.consoleError` 记录堆栈。
2. 回退到纯方块等级：`islandManager.updateLevel(...)` 写入方块等级（无技能加成），`sendLevelResults` 发结果。
3. 避免"内存 `island.level` 已更新（:124 `setLevel(blockLevel)`），DB 未落库（`:161` 在 thenAccept 内被跳过）"
   导致重启后等级回退。

### #56 saveAllDirty 脏标记提前清除致静默丢进度【已实施】

**位置**：`database/PlayerRepository.java`、`task/TaskManager.java`

改动：
- `PlayerRepository.saveTasks` 和 `batchSaveTasks` 改为返回 `boolean`（成功 true / 失败 false，内部已记日志）。
- `TaskManager.savePlayerProgress`：仅 `saveTasks` 返回 true 时才 `dirtyPlayers.remove(uuid)`。
- `TaskManager.saveAllDirty`：先收集所有脏条目到 `toSave` Map（不在循环中 remove），`batchSaveTasks` 成功后才
  遍历 `toSave.keySet()` 清除脏标记；失败则保留全部脏标记，下个自动保存周期（6000 tick）重试。
- `TaskManager.saveAll`（停服路径）：行为不变（无论成败都清，进程即将退出无重试价值）。

### #57 deleteIslandFromDatabase 无条件 return true 致幽灵岛屿【已实施】

**位置**：`database/IslandRepository.java`、`island/IslandManager.java`

改动：
- `IslandRepository.deleteIslandCascade`：移除内部 `catch (SQLException)` 吞异常，改为 `throws SQLException`，
  让调用方感知失败。
- `IslandManager.deleteIslandFromDatabase` / `deleteIsland`：catch `SQLException`，记
  `consoleError` 并返回 `false`（内存索引保留——`deleteIslandFromDatabase` 本就不清索引，
  `deleteIsland` 仅在 DB 成功后 `cleanupIndices`）。
- `IslandDeleteTask` 既已正确判断返回值（`:172-193`），false 路径现可实际触发：玩家收到错误消息、
  不计 deleteCount、内存索引保留（重启后岛屿仍在）。

### #63 IslandLevelCalculator 无同岛并发计算保护【已实施】

**位置**：`level/LevelManager.java`

改动：新增 `Set<UUID> calculating = ConcurrentHashMap.newKeySet()` 防重入守卫。
- `calculateIsland` 入口 `if (!calculating.add(ownerId))` → 发送 `"level.already-calculating"` 消息并返回。
- 冷却检查提前返回时 `calculating.remove(ownerId)` 清理。
- 主线程完成回调 `onCalculationComplete` 外包 `try { ... } finally { calculating.remove(ownerId); }`，
  确保无论成败（包括 #54 的 `exceptionally` 回退路径）都清除守卫。

### 验证

- `./gradlew build --no-build-cache --rerun-tasks` 通过，45 个既有 deprecation 警告，**本次 6 文件零新增警告**。
- `./gradlew test`：5 类 40 用例全绿。
- 产出单一 `build/libs/StarMSkyblock-1.0.0.jar`。
- grep 复查：PlayerRepository 零 `conn.prepareStatement`；LevelManager 零 `getIslandRepository()`；
  `deleteIslandCascade` 声明 `throws SQLException`；`exceptionally` on future chain；`calculating` guard in place。

### 未纳入（有意）

- **#3**（SQLite 单 Connection 并发安全）：用户显式仍冻结。
- **#7**（DB 写移出主线程）：用户显式仍冻结。
- **#9 深度优化**（将既有 `executeInTransaction` 写调用中的只读操作改为 `executeReadTransaction`）：当前无只读
  调用方使用 `executeInTransaction`，`executeReadTransaction` 为基础设施供未来使用。与 #3 解耦后可全面切换。

