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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SQLiteManager {

    private final File dataFolder;
    private Connection connection;
    private final Map<UUID, String> playerNameCache = new ConcurrentHashMap<>();

    public SQLiteManager(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void init() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File dbFile = new File(dataFolder, "islands.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            MessageUtil.consolePrint("&aSQLite 数据库连接成功！");
            createTables();
            migrate();
        } catch (ClassNotFoundException | SQLException e) {
            MessageUtil.consoleError("&c无法连接到 SQLite 数据库！");
            e.printStackTrace();
        }
    }

    private void createTables() {
        String createIslandsTable = "CREATE TABLE IF NOT EXISTS islands (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name VARCHAR(64) DEFAULT ''," +
                "owner_uuid VARCHAR(36) NOT NULL," +
                "level INTEGER DEFAULT 1," +
                "radius INTEGER NOT NULL," +
                "center_x INTEGER NOT NULL," +
                "center_z INTEGER NOT NULL," +
                "permissions TEXT DEFAULT '{}'," +
                "settings TEXT DEFAULT '{}'," +
                "home_data TEXT DEFAULT '{}'," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";

        String createMembersTable = "CREATE TABLE IF NOT EXISTS island_members (" +
                "island_id INTEGER," +
                "player_uuid VARCHAR(36)," +
                "role VARCHAR(16) DEFAULT 'MEMBER'," +
                "PRIMARY KEY (island_id, player_uuid)," +
                "FOREIGN KEY (island_id) REFERENCES islands(id) ON DELETE CASCADE" +
                ");";

        String createPlayersTable = "CREATE TABLE IF NOT EXISTS players (" +
                "player_uuid VARCHAR(36) PRIMARY KEY," +
                "player_name VARCHAR(16) NOT NULL," +
                "border_enabled BOOLEAN DEFAULT 1" +
                ");";

        String createCoopsTable = "CREATE TABLE IF NOT EXISTS island_coops (" +
                "island_id INTEGER," +
                "player_uuid VARCHAR(36)," +
                "PRIMARY KEY (island_id, player_uuid)," +
                "FOREIGN KEY (island_id) REFERENCES islands(id) ON DELETE CASCADE" +
                ");";

        String createPlayerStatsTable = "CREATE TABLE IF NOT EXISTS player_stats (" +
                "player_uuid VARCHAR(36) PRIMARY KEY," +
                "delete_count INTEGER DEFAULT 0" +
                ");";

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

    private void migrate() {
        migrateAddIslandColumns();
    }

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

    // ==================== 玩家名称操作 ====================

    public void savePlayerName(UUID uuid, String playerName) {
        playerNameCache.put(uuid, playerName);
        String sql = "INSERT OR REPLACE INTO players (player_uuid, player_name) VALUES (?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, playerName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            MessageUtil.consoleError("&c保存玩家名称失败！UUID: " + uuid);
            e.printStackTrace();
        }
    }

    public Optional<String> getPlayerName(UUID uuid) {
        // 优先从缓存获取
        String cached = playerNameCache.get(uuid);
        if (cached != null) {
            return Optional.of(cached);
        }
        // 缓存未命中，从数据库加载
        String sql = "SELECT player_name FROM players WHERE player_uuid = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        return Optional.empty();
    }

    // ==================== 边界开关操作 ====================

    public boolean isBorderEnabled(UUID playerUuid) {
        String sql = "SELECT border_enabled FROM players WHERE player_uuid = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        return true;
    }

    public void setBorderEnabled(UUID playerUuid, boolean enabled) {
        String updateSql = "UPDATE players SET border_enabled = ? WHERE player_uuid = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
            pstmt.setBoolean(1, enabled);
            pstmt.setString(2, playerUuid.toString());
            int affected = pstmt.executeUpdate();
            if (affected == 0) {
                String insertSql = "INSERT INTO players (player_uuid, player_name, border_enabled) VALUES (?, '', ?)";
                try (PreparedStatement insertPstmt = conn.prepareStatement(insertSql)) {
                    insertPstmt.setString(1, playerUuid.toString());
                    insertPstmt.setBoolean(2, enabled);
                    insertPstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            MessageUtil.consoleError("&c保存边界开关状态失败！UUID: " + playerUuid);
            e.printStackTrace();
        }
    }

    // ==================== 连接管理 ====================

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                init();
            }
        } catch (SQLException e) {
            init();
        }
        return connection;
    }

    public void close() {
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
