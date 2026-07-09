# StarMSkyblock 优化建议

> 生成日期：2026-07-09
> 分析方式：对 141 个 Java 源文件 + 构建配置 + 资源文件进行三维度并行审查（性能与并发 / 代码质量与架构 / 构建与资源管理），交叉验证后去重汇总。
> 严重度：🔴 HIGH（正确性/数据风险/明显卡顿） · 🟡 MEDIUM（维护性/潜在问题） · 🟢 LOW（清理/微优化）

---

## 目录

- [一、最高优先级问题速览](#一最高优先级问题速览)
- [二、并发与线程安全](#二并发与线程安全)
- [三、数据库与主线程性能](#三数据库与主线程性能)
- [四、等级系统](#四等级系统)
- [五、配置与重载](#五配置与重载)
- [六、内存与资源泄漏](#六内存与资源泄漏)
- [七、代码质量与可维护性](#七代码质量与可维护性)
- [八、构建与部署](#八构建与部署)
- [九、已经做得好的地方（避免误"修"）](#九已经做得好的地方避免误修)
- [十、优先级路线图](#十优先级路线图)

---

## 一、最高优先级问题速览

| # | 严重度 | 问题 | 位置 |
|---|--------|------|------|
| 1 | 🔴 | `BasePermissionManager` 共享可变状态跨玩家串扰，拒绝消息可能发错对象 | `BasePermissionManager.java:54-58,151-262` |
| 2 | 🔴 | `/isadmin reload` 不重载 `AuraSkillsContributionConfig`，却报"全部已重载" | `ReloadCommand.java:19-27` |
| 3 | 🔴 | 几乎所有 DB 写操作都在主线程同步执行 | `IslandRepository.java`、`PlayerRepository.java`、`LevelManager.java:156-169` |
| 4 | 🔴 | 等级扫描每 tick 在主线程同步加载 48 个区块 + 分配快照 | `IslandLevelCalculator.java:143-179` |
| 5 | 🔴 | mcMMO 离线 PowerLevel 查询阻塞主线程（每个离线成员一次磁盘读） | `McMMOIntegration.java:44-86` |
| 6 | 🔴 | i18n 迁移未完成，~210 处硬编码中文绕过消息系统 | `IslandCreateTask.java`、`IslandDeleteTask.java`、`PortalListener.java`、`SkyblockExpansion.java` 等 |
| 7 | 🔴 | `SkyblockExpansion` 6+ 处重复的岛屿查找回退模式（~400 行复制） | `SkyblockExpansion.java:407-813` |
| 8 | 🔴 | `resolve()` 中 `location.getWorld().getName()` 无 null 检查，NPE 风险 | `BasePermissionManager.java:155,188` |

---

## 二、并发与线程安全

### 1. 🔴 BasePermissionManager 共享可变状态跨玩家串扰
**位置**：`permission/BasePermissionManager.java:54-58, 151-184, 236-262`

**问题**：实例字段 `lastCheckWasAreaLocked`、`lastCheckWasPublicArea`、`lastAreaLockedIsland` 在 `resolve()`/`resolveOffline()` 写入，在 `sendDenyMessage()` 读取。12 个子管理器各为全局单例，跨所有玩家复用同一组字段。结果：玩家 A 的一次"locked-area"检查会把字段置为 true，紧接着玩家 B 的权限检查在读取时拿到 A 的状态，**收到错误的拒绝原因**（被提示"locked-area"而实际是普通无权限）。此外 `lastDenyMessageTime`（`:47-52`）是 `accessOrder=true` 的 `LinkedHashMap`，**未**用 `Collections.synchronizedMap` 包裹（对比 `PlayerRepository.createBoundedCache` 已正确处理）。

**修复**：把这三个字段移入 `PermissionCheckResult`（已封装大部分检查状态），每次检查自带上下文；`lastDenyMessageTime` 改 `ConcurrentHashMap` 或参照 `createBoundedCache` 同步包裹。

### 2. 🔴 mcMMO 离线 PowerLevel 查询阻塞主线程
**位置**：`integration/McMMOIntegration.java:44-86`；`level/LevelManager.java:129-163`

**问题**：`LevelManager.onCalculationComplete` 在主线程（`runTask` 调度）执行。当 `useMcMMO=true` 时调用 `McMMOIntegration.getIslandResult(island)`，该方法是**同步**的（返回 `CompletableFuture.completedFuture(...)`），循环对每个离线成员调用 `ExperienceAPI.getPowerLevelOffline(uuid)` —— mcMMO 离线 API 同步读它自己的 SQLite/H2。N 个成员 = N 次主线程磁盘读。由于 future 已完成，`thenAccept` 也在主线程同步执行，整条读路径阻塞主线程直到完成。

**修复**：用 `CompletableFuture.supplyAsync(...)` 包裹 `getIslandResult` 方法体，让离线 API 调用离开主线程，与 AuraSkills 异步路径对齐。

### 3. 🟡 SQLite 单 Connection 被多线程共享，readLock 并发不安全
**位置**：`database/SQLiteManager.java:193-195, 204-231`

**问题**：单个 JDBC `Connection` 被所有读写共享。`ReentrantReadWriteLock` 允许多个并发 reader，但 SQLite JDBC `Connection` **不是线程安全**的 —— 两个 reader 会互相破坏 `Statement`/`ResultSet` 状态。`PlayerRepository.warmUpPlayerNameCache()` 明确异步调度（`StarMSkyblock.java:218`），`TaskManager.saveAllDirty/saveAll` 也异步运行（`TaskManager.java:115-116`）；若玩家在启动预热期间加入，主线程 `getBorderEnabled` 与异步预热会同时处于 `readLock` 内、共享同一 `Connection`。

**修复**：二选一 —— (a) 引入连接池（HikariCP）让每线程独立连接；(b) 禁止并发 reader，所有读也走 `writeLock`（反正底层 Connection 也不支持并发读）。

### 4. 🟡 preparedStatementCache 是裸 HashMap，无内部锁
**位置**：`database/SQLiteManager.java:28, 171-178`

**问题**：`prepareCached()` 的 `get`/`put` 未在 `dbLock` 内（仅靠调用方自觉持锁），任一调用方忘记加锁就会损坏 map（resize 时死循环/丢条目）。仓储层目前一致持锁，但 API 契约脆弱。

**修复**：`prepareCached` 内部自行取锁，或改 `ConcurrentHashMap`。

### 5. 🟡 IslandLevelCalculator.processSnapshots 的 chunksScanned 竞态
**位置**：`level/IslandLevelCalculator.java:322-336`

**问题**：每 tick 提交的 async 任务若未完成，下一 tick 又会调度一个，二者在 Bukkit 异步池并发执行。`chunksScanned += tasks.size()` 是非同步 read-modify-write，会丢增量。当前 `sendProgress` 是空 stub（见 #33），故无可见影响；一旦实现 `sendProgress` 调用 `MessageUtil.send`，还需切回主线程。

**修复**：`chunksScanned` 改 `AtomicInteger` 并 `addAndGet`，或把自增移入已有的 `synchronized (rawTotalCounts)` 块。

### 6. 🟢 LevelManager.cooldowns 是裸 HashMap 且无界
**位置**：`level/LevelManager.java:52`

**修复**：`ConcurrentHashMap` + 容量或时间淘汰（如清除超过 `2 * cooldownSeconds` 的条目）。

---

## 三、数据库与主线程性能

### 7. 🔴 几乎所有 DB 写操作都在主线程
**位置**：`database/IslandRepository.java`（所有 `updateX`：`updateRadius:179-194`、`updateName:213-228`、`updateGeneratorLevel:230-245`、`updateHomeData:264-279`、`updateSettings:297-312`、`savePermissions:314-329`、`updateLevel:575-593`、`updateBaseline:547-563`）；`database/PlayerRepository.java`（`savePlayerName:60-78`、`setBorderEnabled:193-209`、`setFirstNetherJoin:246-263`、`saveTasks:292-308`）；`level/LevelManager.java:156-161,166-169`

**问题**：每个变更方法在持 `dbLock.writeLock()` 时同步 `executeUpdate()`，且从主线程事件处理器/命令路径调用。热路径示例：`BorderListener.onPlayerJoin`（`:69-87`）每次加入最多 3 次串行主线程 DB 操作（`getBorderEnabled` → `savePlayerName` → 可选 `setFirstNetherJoin`）；`/is level` 完成后 `islandRepo.updateLevel(...)` 也在主线程；`PortalListener.tryUnlockNether` 首次进下界时主线程写库。`synchronous=NORMAL` 下每次写约 1–10ms，并发加入突发会产生明显登录卡顿。

**修复**：(a) 批量 + 调度到异步执行器（已有 `batchSaveTasks`/`batchSavePermissions` 模式可推广）；(b) 主线程读应命中已有内存缓存，并给 `PlayerRepository` 加 `borderEnabledCache` 消除 join 时读。

### 8. 🔴 等级扫描每 tick 主线程同步加载 48 个区块 + 分配快照
**位置**：`level/IslandLevelCalculator.java:143-179`

**问题**：每 tick 取 `BATCH_SIZE=16` 位置 × 3 世界 = 最多 48 次 `world.getChunkAt()`（同步磁盘读，未驻留时触发 IO）+ 48 次 `chunk.getChunkSnapshot()`（同步拷贝 16×16×(minY..maxY) 到新对象，每快照 ~98K 条目）。默认半径 8 → 867 位置 ≈ 2.7s 纯 IO；最大半径 32 → ~12675 位置 ≈ 40s 主线程区块加载。另：`LevelManager.CHUNKS_PER_TICK=16`（`:41`）是**死代码**（从不读取），实际速率由 `BATCH_SIZE` 控制。

**修复**：(1) 用 Paper 的 `getChunkAtAsync(x, z, consumer)` 把区块加载移出主线程；(2) 调大 `BATCH_SIZE` 到 32–64（配合异步加载）；(3) 移除死常量 `CHUNKS_PER_TICK` 或真正接线；(4) `finishPhase` 不直接碰 Bukkit API（只用 `MessageUtil` 和仓储写），可整体移到异步线程。

### 9. 🟡 executeInTransaction 对只读回调也取 writeLock，阻塞所有读
**位置**：`database/SQLiteManager.java:204-231`

**问题**：`executeInTransaction` 无条件取 `writeLock`，且在回调体执行期间持有，与 `readLock` 互斥。长时间批量写会阻塞所有 `getPlayerName`/`getUUID`/`isFirstNetherJoin` 读。

**修复**：区分只读事务用 `readLock`（需先解决 #3 的 Connection 共享问题，否则无意义）。

### 10. 🟡 PlayerRepository 未用 prepareCached，每次重编译 SQL
**位置**：`database/PlayerRepository.java` 多处 `conn.prepareStatement(sql)`（`:68,94,126,152,177,199,227,253,276,298,316`）

**问题**：`SQLiteManager.prepareCached`（`:171-178`）完全没被 `PlayerRepository` 使用，每次调用重新解析/规划 SQL。`IslandRepository` 多数正确用了缓存。更糟：`batchSaveTasks`（`:316`）在 try-with-resources 里关闭了缓存语句，**破坏缓存**导致下一个调用方拿不到。

**修复**：改用 `sqliteManager.prepareCached(sql)`，且**不要**关闭缓存语句（生命周期由缓存管理）。

### 11. 🟡 IslandLevelCalculator 递减收益循环 O(overLimit)
**位置**：`level/IslandLevelCalculator.java:226-234`

**问题**：`for (long i=0; i<overLimit; i++) exp += Math.round(Math.max(expValue/(1+decay*i), minimum));` 超阈值方块逐个累加。默认仅 3 种方块有阈值（封顶 30k–50k），可忽略；但若运维设低阈值（如 `COBBLESTONE: 1000`）且岛屿有百万圆石，则变成主线程百万次循环。

**修复**：闭式近似（调和级数 `Σ expValue/(1+decay*i)` 有 digamma 近似）或预计算查表；或把 `finishPhase` 移到异步（见 #8）。

---

## 四、等级系统

（#8、#11、#5 见上方各节）

### 12. 🟢 IslandLevelCalculator 每快照分配 ~98K 条目，内存抖动
**位置**：`level/IslandLevelCalculator.java:161-167`

**修复**：直接遍历区块 section palette（NMS/反射），仅拷贝调色板索引数组（远小于完整快照），仅对非 air 块处理。较大重构。

### 13. 🟢 LevelResults / Island 跨线程变更无安全发布
**位置**：`level/LevelResults.java`；`level/IslandLevelCalculator.java:59,64,322-336`；`level/LevelManager.java:115-172`

**问题**：`LevelResults` 字段是普通非 volatile。`rawTotalCounts`（在 synchronized 块内、且 happens-before `decrementAndGet`）是安全发布的，但 `chunksScanned`（在 synchronized 块外）不是。

**修复**：`chunksScanned` 改 `AtomicInteger`（同时修复 #5）。

---

## 五、配置与重载

### 14. 🔴 /isadmin reload 不重载 AuraSkillsContributionConfig（假成功）
**位置**：`command/subcommand/ReloadCommand.java:19-27`；`StarMSkyblock.java:248-249`

**问题**：reload 调用 `experienceConfig.reload()`（重载 `level.yml` 的 `blocks/limits/diminishing/level-cost/baseline` 段），但**从不**重载 `auraskillsContributionConfig` —— 插件上甚至没有 `getAuraskillsContributionConfig()` getter。改 `level.yml` 的 `auraskills.type/coefficient/max-bonus-level/enabled` 后 reload 仍报"所有配置文件已重载！(耗时 Xms)"，实际无效，需重启。等级结果错误且无任何告警。CLAUDE.md 明确说"新配置文件必须加到这里才能重载" —— 这是潜在的运维陷阱。

**修复**：加 getter + `plugin.getAuraskillsContributionConfig().reload()`。

### 15. 🟡 /isadmin reload 不重载 task 配置（28+ YAML）
**位置**：`ReloadCommand.java:19-27`

**问题**：`TaskConfigScanner` 有 `scan()` 且插件暴露 `getTaskConfigManager()`，但 reload 从不调用。改任务 YAML（新章节、改需求/奖励）不生效，需重启。任务系统是配置最重的子系统。

**修复**：加 `plugin.getTaskConfigManager().scan()`。**注意**：`TaskManager` 内存中的进度引用旧 scan 的 `TaskDefinition` 对象，reload 需重新解析定义，需先评估影响。

### 16. 🟡 reload 非原子，在途操作可能看到新旧配置混合
**位置**：`ReloadCommand.java:19-27`

**问题**：9 个 config manager 顺序重载无同步屏障。reload 窗口内，一次权限检查可能读到刚重载的 `permissionConfigManager` 却仍读旧 `publicAreaConfigManager`/`lockedAreaConfigManager`（它们包装 permission config 且在序列后部重载）。可能产生短暂的权限不一致（绕过或误拒）。

**修复**：reload 期间设 `volatile reloading` 标志让权限监听器短路；或整体快照原子替换。

### 17. 🟡 level.yml 的 auraskills: 段名在 type:mcmmo 时误导
**位置**：`src/main/resources/level.yml:1069-1083`

**问题**：默认 `type: mcmmo`，但外层 YAML key 仍是 `auraskills:`，`AuraSkillsContributionConfig` 也无论选哪个技能插件都从 `auraskills` 段读。配置合并重构遗留的命名不一致。

**修复**：改名为 `skill-contribution:` 或类似中性名。

### 18. 🟢 permissions.yml public-area / locked-area ~95% 重复
**位置**：`src/main/resources/permissions.yml:176-449`

**问题**：两块都枚举全部 ~90 权限和 ~12 设置且值相同。新增权限枚举需在两处加相同默认值。

**修复**：共享 `area-defaults:` 块，两区域仅 override 少数差异项（如 `ENDER_CHEST_OPEN`、`FISHING_ROD_USE`）。

### 19. 🟢 提取的内置文件无版本，升级不覆盖
**位置**：`StarMSkyblock.extractSchematics`/`extractSkyblockMenu`；`TaskConfigScanner.extractBundledTasks`；`LanguageManager.extractBundledZhCN`

**问题**：所有提取器用 `if (target.exists()) skip`，仅文件不存在时拷贝。新版插件更新了 schematic/task/menu/zh_CN 时，老安装永不更新，除非运维手动删文件。

**修复**：嵌入版本标记（写 `.version` 文件或对比 bundled hash），bundled 版本更新时覆盖；或文档说明升级需删旧文件。

---

## 六、内存与资源泄漏

### 20. 🟡 SkullManager.base64Meta 无界，从不淘汰
**位置**：`util/SkullManager.java:44`

**问题**：启动加载 `skin_textures` 全表，每个独特付费玩家加入增长一条（`refreshTexture:92`），从不淘汰。大服上是与独特玩家数成正比的无界内存泄漏。纹理已持久化到 DB，淘汰仅代价一次回读。

**修复**：LRU（`PlayerRepository.createBoundedCache` 模式）或 `PlayerQuitEvent` 淘汰离线玩家，miss 时回读 DB。

### 21. 🟡 PortalListener.lastPortalEnter / BorderListener.borderCache / islandBorderCache 无界不淘汰
**位置**：`listener/PortalListener.java:46`；`listener/BorderListener.java:45,47`

**问题**：`lastPortalEnter` 每次 `EntityPortalEnterEvent`（含非玩家实体）追加，2 秒去重逻辑不删旧条目；`borderCache` 每次 join 填充，quit 不清；`islandBorderCache` 在岛屿删除时**不失效**（`IslandManager.cleanupIndices` 不通知 BorderListener）。长期运行慢泄漏。

**修复**：`PlayerQuitEvent` 淘汰 `borderCache`/`lastPortalEnter`；`cleanupIndices` 回调失效对应 `islandBorderCache`。

### 22. 🟢 extractSchematics 异步与同步 initSchematicManager 竞态
**位置**：`StarMSkyblock.java:129-132`

**问题**：`extractSchematics` 异步派发后立刻同步跑 `initSchematicManager` 和岛屿加载。新装首次启动、3 个 `.schem` 尚不存在时，若粘贴早于提取完成会报"找不到岛屿结构文件"。实际影响小（玩家 `/is create` 时已完成）。

**修复**：同步提取（3 个小文件 <3KB）或 `getEntry`/`loadEntry` 等待提取的 `CompletableFuture`。

---

## 七、代码质量与可维护性

### 23. 🔴 ~210 处硬编码中文，i18n 迁移未完成
**位置**：`island/IslandCreateTask.java`（~25 处，`:56-276`）、`island/IslandDeleteTask.java`（~7 处，`:116-199`）、`listener/PortalListener.java`（`:150,189,224,301,303`）、`placeholder/SkyblockExpansion.java`（"公共区域"/"主世界"/"下界"/"末地"/"已达到最高等级"/"是"/"否"）、`util/OreDisplayName.java`、`permission/IslandPermissionLevel.java`（枚举显示名"岛主"/"管理员"/…）、`level/LevelManager.java:215`

**问题**：CLAUDE.md 说消息键是事实来源且迁移进行中，但 `IslandCreateTask`/`IslandDeleteTask` 用原始 `player.sendMessage("§a岛屿创建成功！")`，**零** `MessageUtil.send` 调用 —— 完全绕过 i18n 系统，丢失 `{name}` 替换和静默标志机制。PAPI 扩展直接向消费者返回硬编码中文维度名/"公共区域"。

**修复**：迁移到 `MessageUtil.send(player, "key", Map.of(...))` 并在 `messages/zh_CN.yml` 加键。枚举显示名最棘手，可按枚举名在展示时经 `MessageUtil` 解析。

### 24. 🔴 SkyblockExpansion 6+ 处重复的岛屿查找回退模式
**位置**：`placeholder/SkyblockExpansion.java:407-813`

**问题**：`getIslandName`/`getPlayerRole`/`getIslandLevelHere`/`getIslandValueHere`/`getGeneratorLevelHere`/`getPlayerOwnIslandName`/`getPlayerOwnRole` 各自重写 `getIslandAt → ifEmpty getIslandAtMaxRange → ifEmpty 返回默认 → try/catch RuntimeException → 返回 fallback`，每个 ~30 行近乎相同，仅末尾提取不同。约 400 行复制粘贴。

**修复**：提取 `Optional<Island> findIslandAt(IslandManager, int chunkX, int chunkZ)`（两次查找都试）和泛型 `<T> T withIslandHere(ctx, T fallback, Function<Island,T> extractor)`，每个方法缩到 3–4 行。

### 25. 🔴 resolve() 中 location.getWorld().getName() 无 null 检查（NPE）
**位置**：`permission/BasePermissionManager.java:155,188`；`setting/BaseSettingManager.java:59`（对比 `:92,114` **有**检查）

**问题**：`Location.getWorld()` 在 Bukkit API 可为 null（无世界构造的 Location、世界卸载期间）。`resolve()` 在每次权限检查热路径上无保护调用 `.getName()`，而 `getIslandAtMaxRange`/`getIslandAt` 正确做了 null 检查。不一致 → null world 在权限检查中抛 NPE，随机取消或放行事件。

**修复**：`resolve()`/`resolveOffline()`/`checkSetting()` 加 `World world = location.getWorld(); if (world == null) return ...;`，或推入共享 helper。

### 26. 🟡 IslandManager.getIslandByPlayerName 用 deprecated getOfflinePlayer(String)
**位置**：`island/IslandManager.java:289-296`（`@SuppressWarnings("deprecation")`）

**问题**：玩家未曾加入时，主线程阻塞于可能的 Mojang API 网络调用。被基于名的命令使用。

**修复**：走 `PlayerRepository` 名字缓存（启动已预热）+ 在线 `Bukkit.getPlayerExact`；拒绝未知的离线按名查找而非触发阻塞 Mojang 请求。

### 27. 🟡 TaskManager 奖励发放逻辑在 claimReward 与 giveForceCompleteRewards 间重复
**位置**：`task/TaskManager.java:416-465` vs `562-630`

**问题**：金币存入、物品掉落、命令派发（含 `server:`/`player:`/默认前缀解析）几乎逐字重复。改奖励逻辑需同步改两处。

**修复**：提取 `grantRewards(UUID uuid, TaskReward rewards, boolean requireOnline)`，两路径都调用。

### 28. 🟡 IslandManager.getIslandAt 与 getIslandAtMaxRange 近乎相同；grid-key 编码重复多处
**位置**：`island/IslandManager.java:659-691, 602-617`；`island/IslandDeleteTask.java:135,143`；`task/listener/BlockBreakTaskListener.chunkKey`

**问题**：两方法仅 `isChunkWithinIsland` vs `isChunkWithinMaxRange` 之差；grid-key 编码 `(((long)gx)<<32)|(gz&0xffffffffL)` 在四处重复。

**修复**：`private Optional<Island> findInGrid(int chunkX, int chunkZ, BiPredicate<Island,int[]> predicate)` 或传 `boolean maxRange` 标志；key 编码集中到 `GridKeys.encodeCell(gx,gz)`。

### 29. 🟡 LevelManager 手写 JSON 序列化（Gson 已可用）
**位置**：`level/LevelManager.java:357-382`

**问题**：`serializeStringMap`/`serializeBlockCounts` 用 `StringBuilder` 手写 JSON，无特殊字符转义（对合法 Material 名低风险但脆弱）。`IslandManager` 已用 `GSON`（`:36`）做反操作。

**修复**：用共享 `Gson`（`new Gson().toJson(map)`）或复用 `IslandSerializer`。

### 30. 🟡 异常静默吞没（含数据丢失风险）
**位置**：
- `task/TaskManager.java:133-137` —— `loadPlayerProgress` 捕获 `gson.fromJson` 的 `Exception` 返回空 map **无日志**。损坏的 task JSON 静默清空玩家任务进度。
- `database/SQLiteManager.java:246` —— `batchWrite` rollback 失败 `catch (SQLException ignored)`（对比 `executeInTransaction:219` 有日志）。
- `util/reflection/FaweReflection.java:71` —— `flush` 中 `catch (Exception ignored)`。
- `task/listener/MoneyTaskListener.java:76`、`task/command/TaskCommand.java:183`、`island/IslandSerializer.java:99` —— `catch (NumberFormatException/IllegalArgumentException ignored)`。

**修复**：至少 TaskManager JSON 解析失败和 SQLite rollback 失败 WARN 日志；FaweReflection/IslandSerializer 至少 `consoleWarn` 一次。

### 31. 🟡 DropPickupPermissionManager 构造器签名与其他 11 个不一致
**位置**：`permission/manager/DropPickupPermissionManager.java:38-42` vs 其他全部

**问题**：唯一接受 `JavaPlugin plugin` 参数（它要调度任务）。其他 11 个都是 4 参数 `(islandManager, configManager, publicAreaConfig, lockedAreaConfig)`。`IslandPermissionManager:78` 对它的构造方式也与其他不同。无法做反射/列表化注册。

**修复**：把 `plugin` 注入基类（让所有 manager 都能调度），或用 `JavaPlugin.getProvidingPlugin(getClass())`（`ToolPermissionManager.syncEntityStatusForPlayer:466` 已这么做）。

### 32. 🟡 StarMSkyblock 是 30+ getter 服务定位单例；Base 类走 getInstance() 而非注入
**位置**：`StarMSkyblock.java:410-514`（30+ getter，静态 `instance`）；`permission/BasePermissionManager.java:156-160,189-193`；`setting/BaseSettingManager.java:60-61,95-97,117`

**问题**：两个 Base 类每次检查都 `StarMSkyblock.getInstance().getWorldManager()` 而非构造器注入（已注入 4 个其他 manager）。把权限热路径耦合到全局单例，且无法单测。12 个子类 × 同一 `getInstance()` 查找。

**修复**：构造器注入 `SkyblockWorldManager`（已传 `islandManager` 等）。顺带让这些类可单测。

### 33. 🟡 PortalListener 坐标计算器互为镜像，中心块计算重复 3 处
**位置**：`listener/PortalListener.java:261-287, 429`

**问题**：`calculateNetherPortalLocation` 偏移 ÷8；`calculateOverworldPortalLocation` 偏移 ×8。中心块计算 `centerChunkX*16+8` 等在两方法 + `getIslandLocation` 重复。

**修复**：提取 `getIslandCenterBlockXZ(Island)` 和 `scalePortalLocation(from, targetWorld, island, double factor)`。

### 34. 🟢 无测试；多个纯逻辑类高价值可测
**位置**：`grid/GridManager.java`（Ulam spiral，纯数学 O(1)）、`level/IslandLevelCalculator.java:340-391`（手写表达式解析器）、`:244-289`（等级公式，`while(true)` 循环有 `cost<=0` 溢出保护是好测试点）、`permission/IslandPermissionLevel.java`（角色层级）、`island/Island.java:586-611`（`hasPermission`）、`task/listener/BlockBreakTaskListener.java:68-94`（位打包）

**修复**：`build.gradle` 加 JUnit 5，把等级公式和表达式解析器抽成纯 helper 类，加测试（spiral 对 0–1000 比参考实现；解析器测运算符优先级/括号；`getManageableRoles`）。

### 35. 🟢 死代码 / 桩
- `level/LevelManager.sendProgress`（`:107-110`）空 stub（"暂时不实现"）。
- `level/LevelManager.getCachedLevel`/`getCachedExperience`（`:263-272`）透传到 `Island` getter，无附加值。
- `task/listener/FarmingTaskListener.onCropGrow`（`:30-35`）调 `incrementProgress(null,...)` 立即 no-op → 整个 `BlockGrowEvent` handler（每个世界每次作物生长都触发）在 `CROPS.contains` 过滤后是死代码。
- `LevelManager.CHUNKS_PER_TICK` 死常量（见 #8）。
- `StarMSkyblock.java:358,364`、`IslandCommand.java:135` 注释掉的 `MessageUtil.consolePrint`。

**修复**：删注释行；`sendProgress` 实现或删；`onCropGrow` 删除或在 TaskManager 实现非玩家种植进度跟踪（可能是原意）。

### 36. 🟢 SimpleDateFormat 每次 createIsland 新建
**位置**：`island/IslandManager.java:230-231`

**修复**：静态 `DateTimeFormatter`（不可变/线程安全），或存 DB 的 `CURRENT_TIMESTAMP` 默认值。

### 37. 🟢 IslandManager 泄漏 IslandRepository 给 LevelManager 直写
**位置**：`island/IslandManager.java:743-745`；`level/LevelManager.java:157,166,343`

**问题**：`LevelManager` 绕过 manager 直写 level/baseline 到 repo，若 manager 日后加写穿缓存则内存与 DB 会分叉。当前因 `LevelManager` 也直接 `island.setLevel(...)` 而暂时 OK，但写路径分裂脆弱。

**修复**：加 `IslandManager.updateLevel(...)`/`updateBaseline(...)` facade，`LevelManager` 调它们。

### 38. 🟢 MoneyTaskListener 无条件常驻轮询 Vault 余额
**位置**：`task/listener/MoneyTaskListener.java:42-51,24`

**问题**：`startBalanceCheckTask` 调度 `runTaskTimer` 周期 200L（10s），`taskScheduled=true` 永久。即使无 EARN_MONEY 任务配置，也每 10s 遍历在线玩家调 `economy.getBalance(player)` —— 部分 Vault 经济后端（如 MySQL 支撑的）用 DB 查询响应。50 在线 = 每 10s 50 次主线程 DB 查询，永久。`taskScheduled` 从不复位，玩家全离线也不停。

**修复**：仅在配置含 `TaskType.EARN_MONEY` 时调度（`TaskManager:65` 已这样 gate 监听器注册）；无在线玩家时取消。

### 39. 🟢 BasePermissionManager.checkPermission(Location,UUID,...) 多余的 Bukkit.getPlayer 查找
**位置**：`permission/BasePermissionManager.java:96-108`

**问题**：UUID 重载调 `Bukkit.getPlayer(uuid)`（哈希查找），而 `ObsidianToLavaListener.onObsidianClick` 和 `DropPickupPermissionManager.onBundleInteract` 调用点已有 `Player`。`Player` 重载（`:113`）存在且避免此查找。Bundle 交互是较热路径。

**修复**：调用点直接传 `player` 而非 `player.getUniqueId()`。

---

## 八、构建与部署

### 40. 🟡 Shadow 插件产出冗余重复 JAR，输出名与 CLAUDE.md 不符
**位置**：`build.gradle:3, 84-86`；产物在 `build/libs/`

**问题**：所有依赖都是 `compileOnly`，无物可 shade。但构建产出两个近似 JAR：`StarMSkyblock-1.0.0.jar`（592KB，plain `jar`）和 `StarMSkyblock-1.0.0-all.jar`（597KB，shadow），仅差 ~5KB（shadow manifest）。CLAUDE.md 说输出是 `build/libs/StarMSkyblock.jar`，但该名文件不存在 —— 部署说明会指向错误产物。

**修复**：加 `shadowJar { archiveClassifier = '' }` 并 `jar { enabled = false }` 产单一 `StarMSkyblock-1.0.0.jar`；或直接删 shadow 插件。更新 CLAUDE.md 匹配实际输出名。

### 41. 🟡 未设 options.release，字节码目标 Java 25，与"Java 21+"文档不符
**位置**：`build.gradle:9-13, 66-69`

**问题**：工具链 `JavaLanguageVersion.of(25)`，`JavaCompile` 未设 `options.release`。无 release 标志则字节码目标 Java 25。CLAUDE.md 称"运行于 Paper 1.26.x，Java 21+" —— 不准确，编译类在 Java 21 运行时抛 `UnsupportedClassVersionError`。

**修复**：加 `options.release = 21`（或真实最低版本）到 `JavaCompile` 配置；或修正 CLAUDE.md 说明需 Java 25。

### 42. 🟢 libs/mcMMO.jar 无版本号，破坏可复现构建
**位置**：`build.gradle:34`；`libs/`

**问题**：`compileOnly files('libs/mcMMO.jar')` 引用无版本号的 jar（不像 `AuraSkills-2.3.12.jar`、`TrMenu-3.12.2.jar`）。实际文件 3.6MB 但来源/版本未固定。无 lockfile/checksum。

**修复**：改名 `mcMMO-<version>.jar` 并更新依赖行；文档记录确切来源。

### 43. 🟢 processResources 冗余 schematics include
**位置**：`build.gradle:77-81`

**问题**：schematics 在 `src/main/resources/schematics/`，默认 `processResources` 已包含。显式 `from` 块重加，`duplicatesStrategy = 'INCLUDE'` 保留两份。功能无害但配置冗余/困惑。

**修复**：删除该块。

### 44. 🟢 plugin.yml 正确完整（无问题）
**位置**：`src/main/resources/plugin.yml`

`api-version: '26.1.2'` 匹配 `spigot-api:26.1.2`。未提交 diff 正确加入 `mcMMO` 到 `softdepend`。两命令两权限声明齐全，`load: POSTWORLD` 合适。无 `depend:` 块但 WorldEdit/FAWE 运行时检查 + `disablePlugin` 回退可接受。

---

## 九、已经做得好的地方（避免误"修"）

> 审查中发现以下设计已正确，列此以免"优化"反而引入问题：

- **`BasePermissionManager.enforce()` / `BaseSettingManager.checkSetting()`** 成功上提了拒绝消息/区域检查样板；12 个权限 + 6 个设置监听器**不是**复制粘贴，复用得当。
- **8 个 task listener** 正确用 `BaseTaskListener.track()`。
- **`SQLiteManager.executeInTransaction`**（`:204-231`）`catch (Throwable)` + rollback 是有据的（防 `RuntimeException` 静默部分提交），`StarMSkyblock.hookTrMenu` 的 `catch (Throwable)` 同理有文档说明。**非**过宽捕获 bug。
- **`ConfigManager`** 用 `volatile` 字段加载时读一次（正确缓存模式；reload 重新填充；无逐访问文件读）。
- **`SkyblockExpansion`** 已把 30 项 `equalsIgnoreCase` 链换成 O(1) `Map` 分发 —— 优秀的前次重构；剩余重复（#24）在 helper 方法而非分发。
- **`GridManager`** O(1) Ulam spiral，无问题。
- **`PlayerRepository.createBoundedCache`**（`:348-356`）正确把 `accessOrder=true` LinkedHashMap 包 `synchronizedMap` 并注释说明原因。3 个缓存 LRU 2000 条。
- **`StarMSkyblock.onDisable`** 调 `HandlerList.unregisterAll(this)`（`:400`）正确防 reload 监听器泄漏。
- **`BorderListener.onPlayerMove`** 在 chunk X/Z 不变时短路（`:119`）。
- **`ContainerPermissionManager.onContainerInteract`** 一次 `resolve(loc,player)` 复用 `PermissionCheckResult` 跨 7+ 检查（`:100`）。
- **任务写已正确批量**（6000 tick 一次 + `executeInTransaction` + `addBatch`）；SQLite WAL + `synchronous=NORMAL` 无 fsync 压力。
- **日志一致**：`src/main/` 内零 `getLogger()`/`System.out`，全走 `MessageUtil.consolePrint/warn/error`；missing-key 去重集合 bounded（有限键空间，reload 清空）。i18n 键完整（无缺失键）。

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

*本报告基于静态代码审查，部分行为性结论（如 mcMMO 离线 API 是否真磁盘读、Vault 后端是否 DB 查询）取决于运行时环境，建议在目标服务器上用 spark/timings 实测验证优先级。*

---

## 十一、实施状态（2026-07-09 实施）

本轮按建议实施了以下改动，全部通过 `./gradlew build` 编译验证。

### 已实施（17 项）

| # | 建议 | 实施内容 |
|---|------|---------|
| #1 | BasePermissionManager 状态串扰 | 三个实例布尔字段改为 `ConcurrentHashMap<UUID, PermissionCheckResult> lastCheckResult`（LRU 上限 512），每个玩家读自己的解析结果；`lastDenyMessageTime` 用 `synchronizedMap` 包裹 |
| #2 | mcMMO 阻塞主线程 | `getIslandResult` 用 `CompletableFuture.supplyAsync` 包裹离线查询；成员 UUID 列表在主线程采集避免跨线程读 island 成员集 |
| #4 | preparedStatementCache 线程安全 | 裸 `HashMap` 改 `ConcurrentHashMap`（语句并发使用仍由 `dbLock` 串行化） |
| #5/#13 | chunksScanned 竞态 | `int` 改 `AtomicInteger`（`addAndGet`/`get`） |
| #6 | cooldowns 非线程安全 | `HashMap` 改 `ConcurrentHashMap` |
| #14 | reload 漏重载 auraskills | 新增 `getAuraskillsContributionConfig()` getter + `ReloadCommand` 调用 `reload()` |
| #15 | reload 漏重载 task | `ReloadCommand` 调用 `getTaskConfigManager().scan()`（已验证 TaskManager 按 ID 查找 def，重载安全） |
| #25 | resolve() NPE | `resolve`/`resolveOffline` 加 `location.getWorld()` null 检查（视为空岛世界之外，不阻断） |
| #29 | LevelManager 手写 JSON | `serializeStringMap`/`serializeBlockCounts` 改用静态 `Gson`（顺带修复转义脆弱性） |
| #30 | TaskManager JSON 静默丢数据 | `loadPlayerProgress` 解析失败改 `MessageUtil.consoleError(msg, e)` 记录堆栈 |
| #36 | SimpleDateFormat | 改静态 `DateTimeFormatter`（不可变/线程安全） |
| #35（部分） | FarmingTaskListener 死代码 | 删除 no-op 的 `onCropGrow`（`incrementProgress(null,...)` 立即返回）及 `BlockGrowEvent` import |
| #38 | MoneyTaskListener 轮询 | 经核查：监听器注册已被 `activeTypes.contains(EARN_MONEY)` 门控（TaskManager），"无 EARN_MONEY 仍轮询"的担忧已不存在；无需改动 |
| #20 | SkullManager 无界缓存 | `base64Meta` 改 bounded `synchronizedMap(LinkedHashMap)`（LRU 上限 2000）；新增 `SQLiteManager.getSkinTexture(UUID)`，缓存未命中时优先回退 DB 再请求 Mojang（避免淘汰后重复请求被限流） |
| #21（部分） | listener 缓存无界 | `BorderListener`/`PortalListener` 各加 `PlayerQuitEvent` 处理器淘汰 `borderCache`/`lastPortalEnter` |
| #40 | Shadow 冗余 JAR | `shadowJar { archiveBaseName='StarMSkyblock'; archiveClassifier=''; archiveVersion='' }` + `jar.enabled=false` -> 产出单一 `build/libs/StarMSkyblock.jar`（与 CLAUDE.md/README 文档一致，删除了 `-all`/带版本号副本） |
| #41 | 字节码目标 Java 25 | 加 `options.release = 21`（对齐"Java 21+"部署要求；编译通过证明代码无 Java 22+ API） |
| #43 | 冗余 schematics include | 删除 `processResources` 中重复的 `from('src/main/resources'){include 'schematics/**'}` 块（默认已包含） |

### 明确推迟（需单独决策或大改，未在本轮处理）

| # | 建议 | 推迟原因 |
|---|------|---------|
| #3 | SQLite 单 Connection 并发不安全 | 需引入 HikariCP 依赖或改全 writeLock，属架构决策 |
| #7 | DB 写移出主线程 | 需异步写队列/批量调度的大规模重构 |
| #8 | 等级扫描 getChunkAtAsync | 需 Paper API 验证 + 大重构（竞态部分 #5/#13 已修） |
| #9/#10 | executeInTransaction readLock / PlayerRepository prepareCached | 依赖 #3；#10 需修 batch 关闭破坏缓存的 bug |
| #11 | 递减收益闭式近似 | 仅极端低阈值配置触发，低优先 |
| #16 | reload 非原子屏障 | 中等改动 |
| #17/#18/#19 | 配置层重命名/去重/版本提取 | 需用户决策或配置迁移 |
| #22 | extractSchematics 竞态 | 实际影响小 |
| #23 | i18n 210 处迁移 | 大工程 |
| #24 | SkyblockExpansion 去重 | 大重构 |
| #26 | getOfflinePlayer(String) deprecated | 需重构 name 查找 |
| #27/#28 | TaskManager 奖励 / grid-key 去重 | 中等改动；#28 跨 4 文件坐标语义去重，非 bug |
| #31/#32/#33 | 构造器注入 / getInstance / PortalListener 去重 | 架构重构 |
| #34 | JUnit 测试 | 独立工程 |
| #37 | IslandManager facade | 低 |
| #42 | libs/mcMMO.jar 版本号 | 需版本信息 |
| #12 | 快照内存优化 | 大重构 |
| #35（其余） | 注释代码 / sendProgress stub | 琐碎；sendProgress 为有意保留的扩展点 |

> 建议优先处理推迟项中的 **#3 + #7**（主线程 DB 卡顿的根本原因）和 **#8**（大半径岛屿等级扫描卡顿），这三项需架构决策，建议结合 spark/timings 实测后单独推进。

---

## 十二、#8 实施更新（2026-07-09 补充）

#8（等级扫描区块异步加载）已实施，采用**反射调用 Paper 运行时 getChunkAtAsync** 的变体（原计划"直接调用"在当前编译依赖下不可行）：

- **原因**：项目编译依赖是 `org.spigotmc:spigot-api:26.1.2`，其 `org.bukkit.World` 接口**没有** `getChunkAtAsync`（Paper 独有 API；Context7 的 snippet 与 spigot-api 实际不符，已反编译 jar 确认 World 仅有同步 `getChunkAt`）。
- **方案**：新增 `IslandLevelCalculator.getChunkAtAsyncCompat(World,int,int,Consumer<Chunk>)`，反射查找并调用 Paper 的 `getChunkAtAsync(int,int,Consumer)`（优先）或 4 参数 `includeNeighbors` 重载；`Method` 缓存（每实例查一次），callback 由 Paper 在主线程执行；非 Paper 或反射失败时回退同步 `getChunkAt`（保持原行为，仅放弃异步收益）。
- **风格**：符合项目"反射兼容跨版本"约定（同 FaweReflection / EnderDragonReflection / WorldBorderReflection）。
- **涉及文件**：`level/IslandLevelCalculator.java`（重写 `loadingPhase` + 新增 `getChunkAtAsyncCompat` + 反射缓存字段）；`level/LevelManager.java`（删除死常量 `CHUNKS_PER_TICK`）。
- **验证**：`./gradlew build` 通过，产出单一 `StarMSkyblock.jar`。
- **运行时待办**：在 Paper 服务器跑 `/is level`（大半径岛屿），确认控制台无"反射调用 getChunkAtAsync 失败"日志（即反射成功走异步）；若出现该日志说明 Paper 无匹配签名，会回退同步（不影响正确性，仅无性能收益）。

---

## 十二(更新)、#8 已回退（2026-07-09）

用户反馈"不需要多版本兼容"，#8 的反射实现已全部回退：

- 删除反射方案（`getChunkAtAsyncCompat` helper、反射缓存字段、`Method`/`Consumer` import）；`loadingPhase` 恢复为原同步实现（`world.getChunkAt` 同步加载 + 同步 snapshot + 直接提交异步 `processSnapshots`）。
- 保留与 #8 无关的 #5/#13 修复（`chunksScanned` AtomicInteger）。
- `LevelManager.CHUNKS_PER_TICK` 死常量已恢复（忠实还原 #8 全部改动；它仍是死代码，可单独清理，归 #35）。
- 回退原因：spigot-api 编译期无 `getChunkAtAsync`（Paper 独有 API），反射调用方案被否决。若未来仍想做 #8，需把编译依赖从 `org.spigotmc:spigot-api` 切到 `io.papermc.paper:paper-api`（版本坐标需确认）后才能直接调用 `getChunkAtAsync`，无需反射。
- `./gradlew build` 通过。
- 已删除计划文件 `docs/superpowers/plans/2026-07-09-level-scan-async.md`。
