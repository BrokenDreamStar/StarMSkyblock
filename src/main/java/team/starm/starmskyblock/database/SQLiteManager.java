package team.starm.starmskyblock.database;

import team.starm.starmskyblock.message.MessageUtil;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

/**
 * SQLite 数据库管理器 —— 负责连接管理、表结构创建/迁移、事务支持和皮肤纹理持久化。
 * 玩家相关的缓存和查询已移至 PlayerRepository。
 */
public class SQLiteManager {

    private final File dataFolder;
    private Connection connection;
    private final Object dbLock = new Object();

    public SQLiteManager(File dataFolder) {
        this.dataFolder = dataFolder;
    }

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
            boolean isNewDatabase = !dbFile.exists();
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            try {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection(url);
                applyPragmas();
                createTables(isNewDatabase);
                migrate();
                MessageUtil.consolePrint("SQLite 数据库连接成功！");
            } catch (ClassNotFoundException | SQLException e) {
                MessageUtil.consoleError("无法连接到 SQLite 数据库！");
                e.printStackTrace();
            }
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

    private void createTables(boolean isNewDatabase) {
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
                    nether_unlocked INTEGER DEFAULT 0
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
            if (isNewDatabase) {
                MessageUtil.consolePrint("数据库表结构创建成功！");
            } else {
                MessageUtil.consolePrint("数据库表结构检查成功！");
            }
        } catch (SQLException e) {
            MessageUtil.consoleError("创建数据库表失败！");
            e.printStackTrace();
        }
    }

    private void migrate() {
        migrateAddIslandColumns();
        migrateAddPlayerColumns();
    }

    private void migrateAddIslandColumns() {
        try (Statement stmt = connection.createStatement()) {
            var rs = stmt.executeQuery("PRAGMA table_info(islands)");
            boolean hasLevel = false;
            boolean hasSettings = false;
            boolean hasPermissions = false;
            boolean hasNetherUnlocked = false;
            boolean hasGeneratorLevel = false;
            boolean hasGeneratorDisabled = false;
            while (rs.next()) {
                String colName = rs.getString("name");
                if ("level".equalsIgnoreCase(colName)) hasLevel = true;
                if ("settings".equalsIgnoreCase(colName)) hasSettings = true;
                if ("permissions".equalsIgnoreCase(colName)) hasPermissions = true;
                if ("nether_unlocked".equalsIgnoreCase(colName)) hasNetherUnlocked = true;
                if ("generator_level".equalsIgnoreCase(colName)) hasGeneratorLevel = true;
                if ("generator_disabled".equalsIgnoreCase(colName)) hasGeneratorDisabled = true;
            }
            rs.close();
            if (!hasLevel) {
                stmt.execute("ALTER TABLE islands ADD COLUMN level INTEGER DEFAULT 1");
                MessageUtil.consolePrint("数据库迁移：已添加 level 列到 islands 表。");
            }
            if (!hasSettings) {
                stmt.execute("ALTER TABLE islands ADD COLUMN settings TEXT DEFAULT '{}'");
                MessageUtil.consolePrint("数据库迁移：已添加 settings 列到 islands 表。");
            }
            if (!hasPermissions) {
                stmt.execute("ALTER TABLE islands ADD COLUMN permissions TEXT DEFAULT '{}'");
                MessageUtil.consolePrint("数据库迁移：已添加 permissions 列到 islands 表。");
            }
            if (!hasNetherUnlocked) {
                stmt.execute("ALTER TABLE islands ADD COLUMN nether_unlocked INTEGER DEFAULT 0");
                MessageUtil.consolePrint("数据库迁移：已添加 nether_unlocked 列到 islands 表。");
            }
            if (!hasGeneratorLevel) {
                stmt.execute("ALTER TABLE islands ADD COLUMN generator_level INTEGER DEFAULT 1");
                MessageUtil.consolePrint("数据库迁移：已添加 generator_level 列到 islands 表。");
            }
            if (!hasGeneratorDisabled) {
                stmt.execute("ALTER TABLE islands ADD COLUMN generator_disabled TEXT DEFAULT '{}'");
                MessageUtil.consolePrint("数据库迁移：已添加 generator_disabled 列到 islands 表。");
            }

            stmt.execute("DROP TABLE IF EXISTS island_permissions");
        } catch (SQLException e) {
            MessageUtil.consoleError("数据库迁移检查 islands 列失败！");
            e.printStackTrace();
        }
    }

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
                MessageUtil.consolePrint("数据库迁移：已添加 first_nether_join 列到 players 表。");
            }
        } catch (SQLException e) {
            MessageUtil.consoleError("数据库迁移检查 players 列失败！");
            e.printStackTrace();
        }
    }

    // ==================== 公共同步锁与连接 ====================

    public Object getDbLock() {
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

    // ==================== 皮肤纹理 ====================

    public void saveSkinTexture(UUID uuid, String texture) {
        String sql = "INSERT OR REPLACE INTO skin_textures (uuid, texture) VALUES (?, ?)";
        synchronized (dbLock) {
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, texture);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("保存皮肤纹理失败！UUID: " + uuid);
                e.printStackTrace();
            }
        }
    }

    public Map<UUID, String> loadAllSkinTextures() {
        Map<UUID, String> result = new java.util.HashMap<>();
        String sql = "SELECT uuid, texture FROM skin_textures";
        synchronized (dbLock) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String texture = rs.getString("texture");
                    result.put(uuid, texture);
                }
            } catch (SQLException e) {
                MessageUtil.consoleError("加载皮肤纹理缓存失败！");
                e.printStackTrace();
            }
        }
        return result;
    }

    // ==================== 连接管理 ====================

    public void close() {
        synchronized (dbLock) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                    MessageUtil.consolePrint("&cSQLite 数据库已关闭。");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
