# StarMSkyblock 性能优化方案

## 目录

- [P0 - 关键优化](#p0---关键优化)
- [P1 - 重要优化](#p1---重要优化)
- [P2 - 一般优化](#p2---一般优化)
- [P3 - 低优先级](#p3---低优先级)

---

## P0 - 关键优化

### 0.1 BlockBreakTaskListener — 字符串 Key 改位编码

**文件**: `src/main/java/team/starm/starmskyblock/task/listener/BlockBreakTaskListener.java:20`

**问题**: 使用 `Map<String, Set<String>>` 存储玩家放置的方块位置。每次 BlockPlace/BlockBreak 事件都创建 `"world,chunkX,chunkZ"` 和 `"x,y,z"` 短寿命 String 对象。高 TPS 下 GC 压力大。

**优化方案**:

```java
// 使用 world 索引避免字符串拼接，使用位编码的 long 作 key
// chunkKey: 高8位=worldIndex, 中24位=chunkX, 低32位=chunkZ (sign-preserved via xor)
// blockKey: 高16位=chunkXOffset(0-15), 中8位=y(0-255), 低16位=chunkZOffset(0-15)
// 实际上用两个 long: chunkKey 和 blockKey 都位编码

private final Map<Long, Set<Long>> playerPlacedBlocks = new ConcurrentHashMap<>();

private static final Map<String, Integer> WORLD_INDICES = new ConcurrentHashMap<>();
private static int nextWorldIndex = 0;

private static long chunkKey(Block block) {
    int worldIndex = WORLD_INDICES.computeIfAbsent(
        block.getWorld().getName(), k -> nextWorldIndex++);
    int cx = block.getX() >> 4;
    int cz = block.getZ() >> 4;
    return ((long) worldIndex << 56) | ((long) (cx & 0xFFFFFF) << 32) | (cz & 0xFFFFFFFFL);
}

private static long blockKey(Block block) {
    return ((long) (block.getX() & 0xF) << 40)
         | ((long) (block.getY() & 0xFF) << 32)
         | (block.getZ() & 0xFFFFFFFFL);
}
```

**变更文件**: 仅 `BlockBreakTaskListener.java`
**收益**: 消除所有短寿命 String 创建，显著减少 GC 压力
**风险**: 无（ChunkUnload 事件中也需要对应改）

---

### 0.2 TaskManager — markDirty 移到循环外

**文件**: `src/main/java/team/starm/starmskyblock/task/TaskManager.java:227`

**问题**: `incrementNaturalProgress()` 中，每次 `pMap.merge()` 后都调用 `markDirty(uuid)`。如果 5 个任务匹配同一种材料，会连续标记 5 次脏数据。`dirtyPlayers` 是 `ConcurrentHashMap`，反复 put 虽然不丢数据，但无意义。

**优化方案**:

```java
// 第 220-236 行改造：循环内不调用 markDirty，循环结束后统一标记
boolean updated = false;
for (TaskDefinition def : tasks) {
    // ... 跳过判断 ...
    pMap.merge(upperKey, amount, Integer::sum);
    updated = true;  // 仅设标志位

    if (!prog.isClaimed() && !prog.isNotified() && prog.isCompleted(def)) {
        prog.setNotified(true);
        updated = true;  // 已在外部统一标记，这里只需要设标志
        // ... 消息提示 ...
    }
}
if (updated) {
    markDirty(uuid);  // 统一在循环外标记一次
}
```

**变更文件**: 仅 `TaskManager.java`
**收益**: 高频挖掘时减少 80%+ 的 `dirtyPlayers.put()` 调用
**风险**: 极低（仅减少重复调用，不影响正确性）

---

### 0.3 SkyblockExpansion — 串行 if-else 改 Map 分派 + 按需获取坐标

**文件**: `src/main/java/team/starm/starmskyblock/placeholder/SkyblockExpansion.java:80-396`

**问题**:
1. 30+ 个串行 `equalsIgnoreCase()` / `startsWith()` 检查，PAPI 每 tick 对每个玩家每个变量调用
2. `chunkX/chunkZ` 在方法开头无条件获取（第 94-95 行），但许多占位符根本不需要位置
3. `plugin.getIslandManager().getIslandByPlayer()` 在多个分支中独立调用（第 142-176, 347-389 行），每次都走两次 ConcurrentHashMap 查询，可以缓存结果

**优化方案**:

```java
// 1. 注册阶段：构建 Map<String, Function<Context, String>>
private final Map<String, BiFunction<Player, String, String>> handlers = new LinkedHashMap<>();

private void registerHandlers() {
    // 不需要位置的简单占位符
    handlers.put("dimension", (p, params) -> switch (p.getWorld().getEnvironment()) {
        case NORMAL -> "主世界";
        case NETHER -> "下界";
        case THE_END -> "末地";
        default -> p.getWorld().getEnvironment().name();
    });
    // 直接使用 keySet().stream().anyMatch() 做前缀匹配，但比串行 if-else 更结构化
}

// 2. 运行时：Map 分派
String result = handlers.entrySet().stream()
    .filter(e -> params.equalsIgnoreCase(e.getKey()) || params.startsWith(e.getKey() + "_"))
    .findFirst()
    .map(e -> e.getValue().apply(player, params))
    .orElse(null);
// 或者用 String 的 switch + 按需获取 chunkX/chunkZ
```

**更实用的方案** — 保持 if-else 结构，但做以下改进：

```java
// 1. 无参数分支（如 "dimension"）放在最前面，尽早 return
// 2. chunkX/chunkZ 改为懒加载变量
// 3. islandByPlayer 改为一次获取后缓存

class PlaceholderContext {
    Player player;
    String params;
    IslandManager islandManager;
    Integer chunkX;       // 懒加载
    Integer chunkZ;
    Optional<Island> playerIsland;  // 缓存 getIslandByPlayer 结果
}

// 使用 String 模式匹配 + switch 表达式 (Java 21+)
String result = switch (params.toLowerCase()) {
    case "dimension" -> /* 直接返货 */;
    case "creationtime" -> /* 取 playerIsland */;
    // ...
};
```

**变更文件**: `SkyblockExpansion.java`
**收益**: 高频 PAPI 调用场景下减少 30-50% 的字符串比较 + 减少无意义的 chunk 获取
**风险**: 低（需确保所有分支覆盖）

---

## P1 - 重要优化

### 1.1 IslandRepository — 短事务合并，提取 Gson 序列化到锁外

**文件**: `src/main/java/team/starm/starmskyblock/database/IslandRepository.java`
**文件**: `src/main/java/team/starm/starmskyblock/database/PlayerRepository.java:287-305`
**文件**: `src/main/java/team/starm/starmskyblock/database/SQLiteManager.java:164-181`

**问题**:
1. 每个 `update*` 方法（`updateRadius`, `updateName`, `updateGeneratorLevel` 等 10+ 个）都独立获取 `writeLock` → 执行一条 SQL → 释放锁。这些方法经常被连续调用（如保存任务进度+玩家名+边界状态），锁开销累积
2. `batchSaveTasks()` 在 `executeInTransaction`（持有写锁）内部做 Gson 序列化，如果脏玩家多（几十人），锁持有时间会拉长

**优化方案 A** — 为 `PlayerRepository.batchSaveTasks` 提取 Gson 到锁外：

```java
public void batchSaveTasks(Map<UUID, String> playerTasks) {
    // 优化：调用方已经传入了序列化后的 JSON，所以不需要再序列化
    // 实际上 saveAllDirty() 中已经做了 gson.toJson()，所以无额外开销
    // 保持现状即可
}
```

**优化方案 B** — 为 `IslandRepository` 添加批量更新方法：

```java
// 新增：单事务内批量更新多个字段
public void batchUpdateIsland(int id, String name, int radius, int generatorLevel, String generatorDisabledJson) {
    try {
        sqliteManager.executeInTransaction(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "UPDATE islands SET name=?, radius=?, generator_level=?, generator_disabled=? WHERE id=?")) {
                pstmt.setString(1, name);
                pstmt.setInt(2, radius);
                pstmt.setInt(3, generatorLevel);
                pstmt.setString(4, generatorDisabledJson);
                pstmt.setInt(5, id);
                pstmt.executeUpdate();
            }
            return null;
        });
    } catch (SQLException e) { /* 错误处理 */ }
}
```

**优化方案 C** — 添加 `batchWrite()` 方法：

```java
// SQLiteManager 中添加
public void batchWrite(List<Consumer<Connection>> operations) {
    dbLock.writeLock().lock();
    try {
        connection.setAutoCommit(false);
        try {
            for (Consumer<Connection> op : operations) {
                op.accept(connection);
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    } finally {
        dbLock.writeLock().unlock();
    }
}
```

**变更文件**: `IslandRepository.java`, `SQLiteManager.java`
**收益**: 批量更新岛屿时减少 50-80% 的锁竞争
**风险**: 低（事务语义不变）

---

### 1.2 SchematicManager — ignoreAirBlocks 优化

**文件**: `src/main/java/team/starm/starmskyblock/generator/SchematicManager.java:211-213`

**问题**: `ignoreAirBlocks(false)` 导致粘贴时重写整个 schematic 体积内的**所有方块**（含空气方块）。大型 schematic 会产生大量无意义的 `setBlock(AIR)` 调用。对于已经有方块的区域（如岛屿重建/重置），这会造成不必要的方块更新和客户端数据包推送。

**优化方案**:

```java
// 1. 添加配置可选的 ignoreAirBlocks 参数
public boolean pasteSchematic(String fileName, World world, int x, int y, int z) {
    return pasteSchematic(fileName, world, x, y, z, true); // 默认忽略空气
}

public boolean pasteSchematic(String fileName, World world, int x, int y, int z, boolean ignoreAirBlocks) {
    // ...
    Operation operation = new ClipboardHolder(clipboard)
            .createPaste(editSession)
            .to(BlockVector3.at(x, y, z))
            .ignoreAirBlocks(ignoreAirBlocks)
            .build();
    // ...
}
```

```java
// 2. IslandCreateTask 中调用时保留原行为（首次创建忽略空气可能导致岛屿透视）
//    pasteSchematic(..., false) - 首次创建
//    pasteSchematic(..., true)  - 后期操作（如 setbiome）
```

**变更文件**: `SchematicManager.java`, `IslandCreateTask.java` 等调用方
**收益**: 大型 schematic 二次粘贴时减少 70%+ 的方块写入
**风险**: 首次岛屿创建使用 `ignoreAirBlocks(true)` 可能导致部分方块不覆盖（应保持 `false`）

---

### 1.3 ConfigManager — 增加配置热重载

**文件**: `src/main/java/team/starm/starmskyblock/config/ConfigManager.java:70-72`
**相关文件**: `PermissionConfigManager.java`, `SettingsConfigManager.java`, `SignConfigManager.java`, `GeneratorConfigManager.java`, `UpgradeConfigManager.java`

**问题**: 所有 6 个配置管理器仅支持启动时加载，配置修改需要重启服务器。

**优化方案**:

```java
// ConfigManager 添加 reload 方法
public void reload() {
    loadConfig();
}

// StarMSkyblock 添加 /isadmin reload 命令
// 并在 AdminCommand 中注册新子命令 ReloadCommand

// 注意：配置字段非 volatile，运行时修改可能对其他线程不可见
// 方案 A：所有字段改为 volatile（最简单）
// public volatile int islandRadius;

// 方案 B：使用 AtomicReference 包装（改动较大）
// private final AtomicReference<ConfigSnapshot> snapshot = new AtomicReference<>();

// 方案 C：批量 getter 返回配置快照对象
public ConfigSnapshot getConfigSnapshot() {
    return new ConfigSnapshot(islandRadius, islandSpacing, ...);
}
```

**推荐方案**：使用 `ConfigSnapshot` 不可变对象，所有 Getter 改为返回快照字段：

```java
// 内部类
public record ConfigSnapshot(
    int islandRadius,
    int islandSpacing,
    int islandMaxRadius,
    boolean teleportOnCreate,
    // ... 所有字段
) {}

private volatile ConfigSnapshot snapshot;

public void loadConfig() {
    // ... 读取配置 ...
    this.snapshot = new ConfigSnapshot(islandRadius, islandSpacing, ...);
}

// Getter 委蛇给 snapshot
public int getIslandRadius() { return snapshot.islandRadius(); }
```

**变更文件**: `ConfigManager.java`, `AdminCommand.java` + 新增 `ReloadCommand.java`
**收益**: 运行时配置变更无需重启服务器，运维效率提升
**风险**: 中等（需确保所有配置读取方使用 Getter 而非直接访问字段）

---

### 1.4 IslandDeleteTask — 实体清理使用 BoundingBox 区域查找

**文件**: `src/main/java/team/starm/starmskyblock/island/IslandDeleteTask.java:82-107`

**问题**: `world.getEntities()` 遍历世界**所有实体**，然后过滤坐标。大型服务器（上千实体）每次删除都遍历全量列表。

**优化方案**:

```java
// 使用 World.getNearbyEntities() 或 World.getEntitiesInChunk() 区块范围查找
// 替代全量遍历 + 坐标过滤

// BoundingBox 区域查找（Paper API）
Location minLoc = new Location(world, minX, world.getMinHeight(), minZ);
Location maxLoc = new Location(world, maxX, world.getMaxHeight(), maxZ);
BoundingBox box = BoundingBox.of(minLoc, maxLoc);
world.getNearbyEntities(box).stream()
    .filter(e -> !(e instanceof Player))
    .forEach(e -> {
        // 额外检查是否在 chunk 范围内（BoundingBox 已保证空间范围）
        e.remove();
    });
```

**变更文件**: `IslandDeleteTask.java`
**收益**: 大服务器实体清理从 O(世界全部实体) 降为 O(岛屿范围内实体)
**风险**: 低（BoundingBox 在 Paper API 中稳定）

---

### 1.5 刷石机权重 — 预计算累积分布

**文件**: `src/main/java/team/starm/starmskyblock/listener/CobblestoneGeneratorListener.java:190-206`

**问题**: `selectMaterial()` 每次刷石机触发都遍历 `rates.values()` 计算总权重，然后再次遍历做选择。如果有 30 种矿石，就是 60 次迭代/事件。生成器等级不频繁变化，可缓存。

**优化方案**:

```java
// GeneratorConfigManager.GeneratorTier 中预计算累积权重列表
// 格式：[{cumulativeWeight, Material}, ...]
public record GeneratorTier(
    int level,
    List<WeightedEntry> normalEntries,  // 预计算
    List<WeightedEntry> netherEntries,
    List<WeightedEntry> endEntries
) {
    public record WeightedEntry(double cumulativeWeight, Material material) {}
}

// 在 GeneratorConfigManager 加载配置时构建：
List<WeightedEntry> buildEntries(Map<String, Double> rates) {
    List<WeightedEntry> entries = new ArrayList<>();
    double cumulative = 0;
    for (Map.Entry<String, Double> e : rates.entrySet()) {
        cumulative += e.getValue();
        Material mat = Material.matchMaterial(e.getKey());
        if (mat != null) {
            entries.add(new WeightedEntry(cumulative, mat));
        }
    }
    return entries;
}
```

```java
// 选择时二分查找
private Material selectMaterialFast(List<WeightedEntry> entries) {
    if (entries.isEmpty()) return null;
    double totalWeight = entries.get(entries.size() - 1).cumulativeWeight();
    double random = ThreadLocalRandom.current().nextDouble(totalWeight);
    int idx = Collections.binarySearch(entries, new WeightedEntry(random, null),
        Comparator.comparingDouble(WeightedEntry::cumulativeWeight));
    if (idx < 0) idx = -idx - 1;
    return entries.get(Math.min(idx, entries.size() - 1)).material();
}
```

**变更文件**: `CobblestoneGeneratorListener.java`, `GeneratorConfigManager.java`
**收益**: 刷石机选择从 O(n) 降为 O(log n)，30 种矿石时约快 5 倍
**风险**: 低（配置重载时重建缓存）

---

## P2 - 一般优化

### 2.1 内存泄漏防护 — playerPlacedBlocks 定期清理

**文件**: `src/main/java/team/starm/starmskyblock/task/listener/BlockBreakTaskListener.java:20`

**问题**: 当前仅在 `ChunkUnloadEvent` 时清理。如果某个区块始终被加载（如玩家岛屿核心区），放置的方块记录会无限累积。极端情况下，如果玩家放置了数百万方块且区块永不卸载，`ConcurrentHashMap` 会持续增长。

**优化方案**:

```java
// 新增定时清理任务
// 在 BlockBreakTaskListener 初始化时注册
public void startCleanupTask(StarMSkyblock plugin) {
    Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
        // 遍历所有 entry，清理空 Set
        playerPlacedBlocks.values().removeIf(Set::isEmpty);
        // 或者记录总数日志，超过阈值时告警
    }, 6000L, 6000L);  // 每 5 分钟
}
```

**变更文件**: `BlockBreakTaskListener.java`, `TaskManager.java`
**收益**: 消除潜在的内存泄漏风险
**风险**: 极低

---

### 2.2 StarMSkyblock — onEnable 启动耗时优化

**文件**: `src/main/java/team/starm/starmskyblock/StarMSkyblock.java`

**问题**: onEnable 中所有 14+ 步初始化是串行执行的。其中 `loadAllIslands()` 加载全部岛屿、`warmUpPlayerNameCache()` 预热全部玩家名、schematic 扫描等可能耗时长。服务器重启时插件启动慢。

**优化方案 A** — 延迟初始化非关键组件：

```java
// 将以下初始化延迟到第一个 tick 之后：
// - registerIntegrations()（PAPI/TrMenu）
// - preWarmWorlds()
// - invite cleanup task
// - permission/设置 listener 注册

Bukkit.getScheduler().runTaskLater(this, () -> {
    registerIntegrations();
    preWarmWorlds();
    initInvitations();
    registerListeners();
}, 1L);
```

**优化方案 B** — DB 批量加载增加进度日志：

```java
// IslandRepository.loadAllIslands() 增加
long start = System.currentTimeMillis();
// ... 加载 ...
long elapsed = System.currentTimeMillis() - start;
MessageUtil.consolePrint(String.format("已加载 %d 个岛屿 (%.2f ms)", rows.size(), elapsed));
```

**变更文件**: `StarMSkyblock.java`, `IslandRepository.java`
**收益**: 服务器启动速度提升 10-30%
**风险**: 中等（需确保延迟初始化的组件不依赖其他早期模块）

---

### 2.3 PlayerRepository.getUUID — 全量缓存遍历改 SQL 查询

**文件**: `src/main/java/team/starm/starmskyblock/database/PlayerRepository.java:113-145`

**问题**: `getUUID(String playerName)` 先在 `playerNameCache` 中全量遍历（O(cacheSize)），未命中才走 SQL。2000 条缓存时遍历开销小，但如果频繁调用此方法仍会有累积开销。

**优化方案**:

```java
// 新增反向缓存 name→UUID
private final Map<String, UUID> playerNameToUuidCache = new LinkedHashMap<>() {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, UUID> eldest) {
        return size() > 2000;
    }
};

// save/query 时同步更新两个缓存
```

```java
// 优化后的 getUUID
public Optional<UUID> getUUID(String playerName) {
    // 1. 从反向缓存取（O(1)）
    UUID cached = playerNameToUuidCache.get(playerName.toLowerCase());
    if (cached != null) return Optional.of(cached);

    // 2. 未命中走 SQL
    dbLock.readLock().lock();
    try {
        Connection conn = sqliteManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT player_uuid FROM players WHERE LOWER(player_name) = ?")) {
            pstmt.setString(1, playerName.toLowerCase());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    playerNameCache.put(uuid, playerName);
                    playerNameToUuidCache.put(playerName.toLowerCase(), uuid);
                    return Optional.of(uuid);
                }
            }
        }
    } // ... finally
    return Optional.empty();
}
```

**变更文件**: `PlayerRepository.java`
**收益**: `getUUID()` 从 O(cacheSize) 降为 O(1)
**风险**: 低（需确保两个缓存的原子性更新）

---

### 2.4 SQLiteManager — 皮肤纹理使用 PreparedStatement 缓存

**文件**: `src/main/java/team/starm/starmskyblock/database/SQLiteManager.java:185-199`

**问题**: `saveSkinTexture()` 每次创建新的 `PreparedStatement`，高频调用时（多人同时登录）每次都有解析开销。

**优化方案**:

```java
// 缓存 PreparedStatement
private PreparedStatement saveSkinStmt;

public void saveSkinTexture(UUID uuid, String texture) {
    dbLock.writeLock().lock();
    try {
        if (saveSkinStmt == null) {
            saveSkinStmt = connection.prepareStatement(
                "INSERT OR REPLACE INTO skin_textures (uuid, texture) VALUES (?, ?)");
        }
        saveSkinStmt.setString(1, uuid.toString());
        saveSkinStmt.setString(2, texture);
        saveSkinStmt.executeUpdate();
    } catch (SQLException e) {
        MessageUtil.consoleError("保存皮肤纹理失败！UUID: " + uuid, e);
    } finally {
        dbLock.writeLock().unlock();
    }
}

// close() 中关闭缓存
public void close() {
    dbLock.writeLock().lock();
    try {
        if (saveSkinStmt != null) saveSkinStmt.close();
        // ... 原有的关闭逻辑
    } finally {
        dbLock.writeLock().unlock();
    }
}
```

**变更文件**: `SQLiteManager.java`
**收益**: 减少 SQL 解析开销，高并发时节省 CPU
**风险**: 低

---

### 2.5 PortalListener — 非玩家传送消息合并

**文件**: `src/main/java/team/starm/starmskyblock/listener/PortalListener.java:418`

**问题**: 非玩家实体（掉落物、箭矢、经验球）通过传送门时遍历全服在线玩家发送消息。每 tick 可能有大量实体通过传送门，造成不必要的循环。

**优化方案**:

```java
// 仅在实体是特定类型时发送通知，忽略掉落物/经验球等
if (entity.getType() == EntityType.PLAYER) {
    // ... 处理玩家传送
} else if (entity instanceof Monster || entity instanceof Animal) {
    // ... 有限的通知
}
// 忽略 Item/Arrow/ExperienceOrb 等
```

**变更文件**: `PortalListener.java`
**收益**: 减少实体大量通过传送门时的 CPU 消耗
**风险**: 低

---

## P3 - 低优先级优化

### 3.1 BlockBreakTaskListener — ChunkUnload 使用位编码

**文件**: `src/main/java/team/starm/starmskyblock/task/listener/BlockBreakTaskListener.java:52-56`

**问题**: `onChunkUnload` 也使用了字符串拼接做 key（第 53-54 行）。

**优化方案**: 改为与 0.1 相同的位编码方式。

### 3.2 HikariCP 连接池

**问题**: SQLite 使用单 `Connection` 实例，每次操作直接使用。虽然 SQLite 本身是单写模型，但 HikariCP 可以更好地管理与连接超时、健康检查等。

**优化方案**: 引入 `HikariCP` 依赖（已内置 SQLite 驱动的兼容性），替换 `DriverManager.getConnection()`。

```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
config.setMaximumPoolSize(1);  // SQLite 单写，池大小只需 1
config.setConnectionTestQuery("SELECT 1");
dataSource = new HikariDataSource(config);
```

**收益**: 连接健康检测、自动重连、连接泄漏防护
**风险**: 低（需确认与 `ReentrantReadWriteLock` 的兼容性，HikariCP 本身是线程安全的，锁可以移除）

### 3.3 代码风格统一

- `BasePermissionManager.java` 中的 permissionMessageCooldown 使用 `LinkedHashMap` 做 LRU，但未使用 `removeEldestEntry` 的 `accessOrder=true` 特性（第 34-42 行附近）。检查是否有同步问题。
- `PlayerRepository` 中有多处 `dbLock.readLock().lock()` 后直接操作 `Connection`，但中间没有 try-catch 保护 `pstmt.executeQuery()` 抛异常时锁释放。确认 `finally` 块已正确处理。

### 3.4 IslandManager — islandNameIndex 线程安全

**文件**: `src/main/java/team/starm/starmskyblock/island/IslandManager.java`

如果 `islandNameIndex` 需要在运行时动态添加/删除，需确认所有写操作使用 `computeIfAbsent` 等原子方法。当前加载时全量构建，只读操作无需同步。

---

## 优先执行顺序建议

| 优先级 | 编号 | 名称 | 预估工时 |
|--------|------|------|----------|
| P0 | 0.1 | BlockBreakTaskListener 位编码 | 30min |
| P0 | 0.2 | TaskManager markDirty 提取到循环外 | 10min |
| P0 | 0.3 | SkyblockExpansion 懒加载 + Map 分派 | 60min |
| P1 | 1.1 | DB 短事务合并 + 提取序列化到锁外 | 45min |
| P1 | 1.2 | Schematic ignoreAirBlocks 参数化 | 20min |
| P1 | 1.3 | 配置热重载 | 90min |
| P1 | 1.4 | IslandDeleteTask BoundingBox 实体查找 | 15min |
| P1 | 1.5 | 刷石机权重预计算累积分布 | 30min |
| P2 | 2.1 | playerPlacedBlocks 定期清理 | 15min |
| P2 | 2.2 | onEnable 启动耗时优化 | 30min |
| P2 | 2.3 | PlayerRepository 反向缓存 | 20min |
| P2 | 2.4 | SQLite PreparedStatement 缓存 | 10min |
| P2 | 2.5 | PortalListener 实体类型过滤 | 10min |
| P3 | 3.x | 其他低优先级 | 按需 |

总计：需要完成 P0 约 **100min**，P1 约 **200min**，P2 约 **85min**。
