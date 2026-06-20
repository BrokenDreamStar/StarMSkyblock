package team.starm.starmskyblock.database;

import team.starm.starmskyblock.message.MessageUtil;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * SQLite 数据库管理器 —— 负责连接管理、表结构创建、事务支持和皮肤纹理持久化。
 * 玩家相关的缓存和查询已移至 PlayerRepository。
 */
public class SQLiteManager {

    private final File dataFolder;
    private Connection connection;
    private PreparedStatement saveSkinStmt;
    /** 按 SQL 语句缓存的 PreparedStatement,避免每次执行重新编译。在 close() 中统一释放。 */
    private final java.util.Map<String, PreparedStatement> preparedStatementCache = new java.util.HashMap<>();
    private final ReentrantReadWriteLock dbLock = new ReentrantReadWriteLock();

    public SQLiteManager(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void init() {
        dbLock.writeLock().lock();
        try {
            if (connection != null) {
                closePreparedStatements();
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
                MessageUtil.consolePrint("SQLite 数据库连接成功！");
            } catch (ClassNotFoundException | SQLException e) {
                MessageUtil.consoleError("无法连接到 SQLite 数据库！", e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    private void applyPragmas() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA busy_timeout=5000");
            stmt.execute("PRAGMA foreign_keys=ON");
            stmt.execute("PRAGMA cache_size=-64000");
        }
    }

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
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    nether_unlocked INTEGER DEFAULT 0,
                    generator_level INTEGER DEFAULT 1,
                    generator_disabled TEXT DEFAULT '{}',
                    total_points REAL DEFAULT 0,
                    block_counts TEXT DEFAULT '{}',
                    baseline_total_points REAL DEFAULT 0,
                    baseline_block_counts TEXT DEFAULT '{}',
                    auraskills_contribution INTEGER DEFAULT 0
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
                    border_enabled BOOLEAN DEFAULT 0,
                    first_nether_join BOOLEAN DEFAULT 1,
                    tasks TEXT DEFAULT '{}'
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

        String createSkinTexturesTable = """
                CREATE TABLE IF NOT EXISTS skin_textures (
                    uuid VARCHAR(36) PRIMARY KEY,
                    texture TEXT NOT NULL
                );""";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createIslandsTable);
            stmt.execute(createMembersTable);
            stmt.execute(createCoopsTable);
            stmt.execute(createPlayersTable);
            stmt.execute(createPlayerStatsTable);
            stmt.execute(createSkinTexturesTable);

            MessageUtil.consolePrint("数据库表结构创建成功！");
        } catch (SQLException e) {
            MessageUtil.consoleError("创建数据库表失败！", e);
        }
    }

    /**
     * 执行简单更新语句（用于迁移）
     */
    private void executeUpdate(String sql) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    // ==================== PreparedStatement 缓存 ====================

    /**
     * 获取或创建缓存的 PreparedStatement。
     * 调用方<b>不应当</b>在 try-with-resources 中关闭返回的语句,关闭由 {@link #close()} 统一执行。
     * 注意:当连接重新创建(init())时,缓存的语句会失效,届时需由调用方调用
     * {@link #closePreparedStatements()} 或重新 init。
     */
    public PreparedStatement prepareCached(String sql) throws SQLException {
        PreparedStatement ps = preparedStatementCache.get(sql);
        if (ps == null || ps.isClosed()) {
            ps = connection.prepareStatement(sql);
            preparedStatementCache.put(sql, ps);
        }
        return ps;
    }

    public void closePreparedStatements() {
        for (PreparedStatement ps : preparedStatementCache.values()) {
            try { ps.close(); } catch (SQLException ignored) {}
        }
        preparedStatementCache.clear();
    }

    // ==================== 公共同步锁与连接 ====================

    public ReentrantReadWriteLock getDbLock() {
        return dbLock;
    }

    public Connection getConnection() {
        return connection;
    }

    // ==================== 事务辅助 ====================

    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute(Connection conn) throws SQLException;
    }

    public <T> T executeInTransaction(TransactionCallback<T> callback) throws SQLException {
        dbLock.writeLock().lock();
        try {
            connection.setAutoCommit(false);
            try {
                T result = callback.execute(connection);
                connection.commit();
                return result;
            } catch (Throwable t) {
                // 必须 catch Throwable 而非 SQLException：callback 抛 RuntimeException 时
                // 原实现只 catch SQLException，rollback() 不会执行，finally 的
                // setAutoCommit(true) 会把已写入的部分静默提交。
                try {
                    connection.rollback();
                } catch (SQLException re) {
                    MessageUtil.consoleWarn("事务回滚失败: " + re.getMessage());
                }
                if (t instanceof SQLException sqle) throw sqle;
                if (t instanceof Error error) throw error;
                if (t instanceof RuntimeException re) throw re;
                throw new RuntimeException(t);
            } finally {
                connection.setAutoCommit(true);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public void batchWrite(List<Consumer<Connection>> operations) {
        dbLock.writeLock().lock();
        try {
            connection.setAutoCommit(false);
            try {
                for (Consumer<Connection> op : operations) {
                    op.accept(connection);
                }
                connection.commit();
            } catch (RuntimeException | SQLException e) {
                // 回调抛 RuntimeException 或 SQL 失败时统一 rollback，
                // 避免半提交数据残留。原实现仅 catch SQLException，
                // 回调 RuntimeException 会跳过 rollback 直接外溢。
                try { connection.rollback(); } catch (SQLException ignored) {}
                throw e instanceof RuntimeException re ? re : new RuntimeException(e);
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException e) {
                    MessageUtil.consoleWarn("batchWrite 恢复 autoCommit 失败");
                }
            }
        } catch (SQLException e) {
            // 仅 setAutoCommit(false) 失败路径：未进入事务，无需 rollback。
            throw new RuntimeException(e);
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    // ==================== 皮肤纹理 ====================

    public void saveSkinTexture(UUID uuid, String texture) {
        dbLock.writeLock().lock();
        try {
            if (saveSkinStmt == null || saveSkinStmt.isClosed()) {
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

    public Map<UUID, String> loadAllSkinTextures() {
        Map<UUID, String> result = new java.util.HashMap<>();
        String sql = "SELECT uuid, texture FROM skin_textures";
        dbLock.readLock().lock();
        try {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String texture = rs.getString("texture");
                    result.put(uuid, texture);
                }
            } catch (SQLException e) {
                MessageUtil.consoleError("加载皮肤纹理缓存失败！", e);
            }
        } finally {
            dbLock.readLock().unlock();
        }
        return result;
    }

    // ==================== 连接管理 ====================

    public void close() {
        dbLock.writeLock().lock();
        try {
            try {
                closePreparedStatements();
                if (saveSkinStmt != null && !saveSkinStmt.isClosed()) {
                    saveSkinStmt.close();
                }
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                    MessageUtil.consolePrint("&cSQLite 数据库已关闭。");
                }
            } catch (SQLException e) {
                MessageUtil.consoleError("关闭数据库连接时发生错误", e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }
}
