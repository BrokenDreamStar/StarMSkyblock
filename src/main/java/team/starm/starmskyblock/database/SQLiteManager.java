package team.starm.starmskyblock.database;

import team.starm.starmskyblock.message.MessageUtil;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite 数据库管理器 —— 负责岛屿系统的全部持久化操作。
 * <p>
 * 管理以下核心表：islands（岛屿）、island_members（成员）、players（玩家）、
 * island_coops（合作者）、player_stats（玩家统计）。
 * 提供同步 API（内部锁 + WAL 模式）以支持多线程安全写入，并内置 LRU 缓存加速高频读取。
 */
public class SQLiteManager {

    /** 插件数据文件夹路径 */
    private final File dataFolder;
    /** SQLite 连接实例（延迟初始化） */
    private Connection connection;
    /** 数据库操作全局锁，所有读写都经过此锁同步 */
    private final Object dbLock = new Object();

    /** 玩家名称缓存（容量 2000，LRU 淘汰），避免反复查库 */
    private final Map<UUID, String> playerNameCache = createBoundedCache(2000);
    /** 首次进入下界标记缓存（容量 2000，LRU 淘汰） */
    private final Map<UUID, Boolean> firstNetherJoinCache = createBoundedCache(2000);

    public SQLiteManager(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    /**
     * 初始化数据库连接：创建数据文件夹 → 建立 JDBC 连接 → 设置 PRAGMA → 建表 → 迁移旧结构。
     * 如果已有连接则先关闭再重建（支持重载）。
     */
    public void init() {
        synchronized (dbLock) {
            if (connection != null) {
                try { connection.close(); } catch (SQLException ignored) {}
            }
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File dbFolder = new File(dataFolder, "database");
            if (!dbFolder.exists()) {
                dbFolder.mkdirs();
            }

            File dbFile = new File(dbFolder, "islands.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            try {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection(url);
                applyPragmas();
                createTables();
                migrate();
                MessageUtil.consolePrint("&aSQLite 数据库连接成功！");
            } catch (ClassNotFoundException | SQLException e) {
                MessageUtil.consoleError("&c无法连接到 SQLite 数据库！");
                e.printStackTrace();
            }
        }
    }

    /**
     * 应用 SQLite 运行时优化 PRAGMA：
     * WAL → 读写不互斥；synchronous=NORMAL → 平衡性能与安全；
     * busy_timeout=5s → 避免并发忙锁报错；启用外键约束；64MB 页缓存。
     */
    private void applyPragmas() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA busy_timeout=5000");
            stmt.execute("PRAGMA foreign_keys=ON");
            stmt.execute("PRAGMA cache_size=-64000");
        }
    }

    /**
     * 创建五张核心业务表（IF NOT EXISTS）。
     * islands 存储岛屿属性，island_members 管理多对多成员关系，
     * players 存储玩家偏好，island_coops 管理合作者关系，player_stats 记录统计。
     */
    private void createTables() {
        String createIslandsTable = """
                CREATE TABLE IF NOT EXISTS islands (
                    id INTEGER PRIMARY KEY,
                    name VARCHAR(64) DEFAULT '',
                    owner_uuid VARCHAR(36) NOT NULL,
                    level INTEGER DEFAULT 1,
                    radius INTEGER NOT NULL,
                    center_x INTEGER NOT NULL,
                    center_z INTEGER NOT NULL,
                    permissions TEXT DEFAULT '{}',
                    settings TEXT DEFAULT '{}',
                    home_data TEXT DEFAULT '{}',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );""";

        String createMembersTable = """
                CREATE TABLE IF NOT EXISTS island_members (
                    island_id INTEGER,
                    player_uuid VARCHAR(36),
                    role VARCHAR(16) DEFAULT 'MEMBER',
                    PRIMARY KEY (island_id, player_uuid),
                    FOREIGN KEY (island_id) REFERENCES islands(id) ON DELETE CASCADE
                );""";

        String createPlayersTable = """
                CREATE TABLE IF NOT EXISTS players (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(16) NOT NULL,
                    border_enabled BOOLEAN DEFAULT 1,
                    first_nether_join BOOLEAN DEFAULT 1
                );""";

        String createCoopsTable = """
                CREATE TABLE IF NOT EXISTS island_coops (
                    island_id INTEGER,
                    player_uuid VARCHAR(36),
                    PRIMARY KEY (island_id, player_uuid),
                    FOREIGN KEY (island_id) REFERENCES islands(id) ON DELETE CASCADE
                );""";

        String createPlayerStatsTable = """
                CREATE TABLE IF NOT EXISTS player_stats (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    delete_count INTEGER DEFAULT 0
                );""";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createIslandsTable);
            stmt.execute(createMembersTable);
            stmt.execute(createCoopsTable);
            stmt.execute(createPlayersTable);
            stmt.execute(createPlayerStatsTable);
            MessageUtil.consolePrint("&a数据库表结构检查/创建完毕。");
        } catch (SQLException e) {
            MessageUtil.consoleError("&c创建数据库表失败！");
            e.printStackTrace();
        }
    }

    /**
     * 执行数据库迁移：检查旧版表结构，缺少的列自动 ADD COLUMN。
     */
    private void migrate() {
        migrateAddIslandColumns();
        migrateAddPlayerColumns();
    }

    /**
     * 迁移 islands 表：检查并添加 level、settings、permissions 列（旧版升级用）。
     * 同时清理旧版孤立的 island_permissions 表。
     */
    private void migrateAddIslandColumns() {
        try (Statement stmt = connection.createStatement()) {
            var rs = stmt.executeQuery("PRAGMA table_info(islands)");
            boolean hasLevel = false;
            boolean hasSettings = false;
            boolean hasPermissions = false;
            while (rs.next()) {
                String colName = rs.getString("name");
                if ("level".equalsIgnoreCase(colName)) hasLevel = true;
                if ("settings".equalsIgnoreCase(colName)) hasSettings = true;
                if ("permissions".equalsIgnoreCase(colName)) hasPermissions = true;
            }
            rs.close();
            if (!hasLevel) {
                stmt.execute("ALTER TABLE islands ADD COLUMN level INTEGER DEFAULT 1");
                MessageUtil.consolePrint("&a数据库迁移：已添加 level 列到 islands 表。");
            }
            if (!hasSettings) {
                stmt.execute("ALTER TABLE islands ADD COLUMN settings TEXT DEFAULT '{}'");
                MessageUtil.consolePrint("&a数据库迁移：已添加 settings 列到 islands 表。");
            }
            if (!hasPermissions) {
                stmt.execute("ALTER TABLE islands ADD COLUMN permissions TEXT DEFAULT '{}'");
                MessageUtil.consolePrint("&a数据库迁移：已添加 permissions 列到 islands 表。");
            }

            stmt.execute("DROP TABLE IF EXISTS island_permissions");
        } catch (SQLException e) {
            MessageUtil.consoleError("&c数据库迁移检查 islands 列失败！");
            e.printStackTrace();
        }
    }

    /**
     * 迁移 players 表：添加 first_nether_join 列（旧版升级用）。
     */
    private void migrateAddPlayerColumns() {
        try (Statement stmt = connection.createStatement()) {
            var rs = stmt.executeQuery("PRAGMA table_info(players)");
            boolean hasFirstNetherJoin = false;
            while (rs.next()) {
                String colName = rs.getString("name");
                if ("first_nether_join".equalsIgnoreCase(colName)) hasFirstNetherJoin = true;
            }
            rs.close();
            if (!hasFirstNetherJoin) {
                stmt.execute("ALTER TABLE players ADD COLUMN first_nether_join BOOLEAN DEFAULT 1");
                MessageUtil.consolePrint("&a数据库迁移：已添加 first_nether_join 列到 players 表。");
            }
        } catch (SQLException e) {
            MessageUtil.consoleError("&c数据库迁移检查 players 列失败！");
            e.printStackTrace();
        }
    }

    // ==================== 公共同步锁 ====================

    /** 返回全局数据库锁对象，供外部配合 synchronized 使用 */
    public Object getDbLock() {
        return dbLock;
    }

    /** 返回底层 SQLite 连接（调用方需自行同步） */
    public Connection getConnection() {
        return connection;
    }

    // ==================== 事务辅助 ====================

    /** 事务回调接口，用于 executeInTransaction */
    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute(Connection conn) throws SQLException;
    }

    /**
     * 在事务中执行一组数据库操作：自动 begin → commit / rollback。
     * 多个写操作合并为一个事务可大幅提升批量写入性能。
     */
    public <T> T executeInTransaction(TransactionCallback<T> callback) throws SQLException {
        synchronized (dbLock) {
            connection.setAutoCommit(false);
            try {
                T result = callback.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    // ==================== 玩家名称操作 ====================

    /** 保存玩家名称到数据库，同时更新缓存 */
    public void savePlayerName(UUID uuid, String playerName) {
        String sql = "INSERT INTO players (player_uuid, player_name) VALUES (?, ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET player_name = excluded.player_name";
        synchronized (dbLock) {
            playerNameCache.put(uuid, playerName);
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, playerName);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("&c保存玩家名称失败！UUID: " + uuid);
                e.printStackTrace();
            }
        }
    }

    /** 获取玩家名称：先查缓存，未命中再查库并回填缓存 */
    public Optional<String> getPlayerName(UUID uuid) {
        synchronized (dbLock) {
            String cached = playerNameCache.get(uuid);
            if (cached != null) {
                return Optional.of(cached);
            }
        }
        String sql = "SELECT player_name FROM players WHERE player_uuid = ?";
        synchronized (dbLock) {
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String name = rs.getString("player_name");
                        playerNameCache.put(uuid, name);
                        return Optional.of(name);
                    }
                }
            } catch (SQLException e) {
                MessageUtil.consoleError("&c获取玩家名称失败！UUID: " + uuid);
                e.printStackTrace();
            }
        }
        return Optional.empty();
    }

    // ==================== 边界开关操作 ====================

    /** 查询玩家是否启用了岛屿边界显示，默认 true */
    public boolean isBorderEnabled(UUID playerUuid) {
        String sql = "SELECT border_enabled FROM players WHERE player_uuid = ?";
        synchronized (dbLock) {
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean("border_enabled");
                    }
                }
            } catch (SQLException e) {
                MessageUtil.consoleError("&c获取边界开关状态失败！UUID: " + playerUuid);
                e.printStackTrace();
            }
        }
        return true;
    }

    /** 设置玩家岛屿边界显示开关 */
    public void setBorderEnabled(UUID playerUuid, boolean enabled) {
        String sql = "INSERT INTO players (player_uuid, player_name, border_enabled) VALUES (?, '', ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET border_enabled = excluded.border_enabled";
        synchronized (dbLock) {
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                pstmt.setBoolean(2, enabled);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("&c保存边界开关状态失败！UUID: " + playerUuid);
                e.printStackTrace();
            }
        }
    }

    // ==================== 首次进入下界操作 ====================

    /** 判断玩家是否为首次进入下界（用于新手引导式传送），使用缓存加速 */
    public boolean isFirstNetherJoin(UUID playerUuid) {
        synchronized (dbLock) {
            Boolean cached = firstNetherJoinCache.get(playerUuid);
            if (cached != null) {
                return cached;
            }
        }
        String sql = "SELECT first_nether_join FROM players WHERE player_uuid = ?";
        synchronized (dbLock) {
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        boolean value = rs.getBoolean("first_nether_join");
                        firstNetherJoinCache.put(playerUuid, value);
                        return value;
                    }
                }
            } catch (SQLException e) {
                MessageUtil.consoleError("&c获取首次进入下界状态失败！UUID: " + playerUuid);
                e.printStackTrace();
            }
            firstNetherJoinCache.put(playerUuid, true);
            return true;
        }
    }

    /** 设置玩家首次进入下界标记（进入过一次后置为 false） */
    public void setFirstNetherJoin(UUID playerUuid, boolean firstJoin) {
        String sql = "INSERT INTO players (player_uuid, player_name, first_nether_join) VALUES (?, '', ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET first_nether_join = excluded.first_nether_join";
        synchronized (dbLock) {
            firstNetherJoinCache.put(playerUuid, firstJoin);
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                pstmt.setBoolean(2, firstJoin);
                pstmt.setBoolean(3, firstJoin);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("&c保存首次进入下界状态失败！UUID: " + playerUuid);
                e.printStackTrace();
            }
        }
    }

    // ==================== 连接管理 ====================

    /** 关闭数据库连接，释放资源（插件禁用时调用） */
    public void close() {
        synchronized (dbLock) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                    MessageUtil.consolePrint("&aSQLite 数据库已关闭。");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // ==================== 有界缓存 ====================

    /** 创建基于 LinkedHashMap 的 LRU 有界缓存，访问顺序排序，超限时淘汰最久未使用项 */
    private static <K, V> Map<K, V> createBoundedCache(int maxSize) {
        return new LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        };
    }

    /** 使指定玩家的名称缓存失效（名称变更时调用） */
    public void invalidatePlayerNameCache(UUID uuid) {
        playerNameCache.remove(uuid);
    }

    /** 使指定玩家的首次下界标记缓存失效 */
    public void invalidateFirstNetherJoinCache(UUID uuid) {
        firstNetherJoinCache.remove(uuid);
    }

    /** 清空所有本地缓存（重载数据时调用） */
    public void clearCaches() {
        playerNameCache.clear();
        firstNetherJoinCache.clear();
    }
}
