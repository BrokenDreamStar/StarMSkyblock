package team.starm.starmskyblock.database;

import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

public class SQLiteManager {

    private final StarMSkyblock plugin;
    private Connection connection;

    public SQLiteManager(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public void init() {
        // 确保插件数据文件夹存在
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File dbFile = new File(plugin.getDataFolder(), "islands.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            MessageUtil.consolePrint("&aSQLite 数据库连接成功！");
            createTables();
        } catch (ClassNotFoundException | SQLException e) {
            MessageUtil.consoleError("&c无法连接到 SQLite 数据库！");
            e.printStackTrace();
        }
    }

    private void createTables() {
        String createIslandsTable = "CREATE TABLE IF NOT EXISTS islands (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "owner_uuid VARCHAR(36) NOT NULL," +
                "name VARCHAR(64) DEFAULT ''," +
                "radius INTEGER NOT NULL," +
                "center_x INTEGER NOT NULL," +
                "center_z INTEGER NOT NULL," +
                "custom_home_x REAL DEFAULT 0," +
                "custom_home_y REAL DEFAULT 0," +
                "custom_home_z REAL DEFAULT 0," +
                "has_custom_home BOOLEAN DEFAULT 0," +
                "normal_home_x REAL DEFAULT 0," +
                "normal_home_y REAL DEFAULT 0," +
                "normal_home_z REAL DEFAULT 0," +
                "has_normal_home BOOLEAN DEFAULT 0," +
                "nether_home_x REAL DEFAULT 0," +
                "nether_home_y REAL DEFAULT 0," +
                "nether_home_z REAL DEFAULT 0," +
                "has_nether_home BOOLEAN DEFAULT 0," +
                "end_home_x REAL DEFAULT 0," +
                "end_home_y REAL DEFAULT 0," +
                "end_home_z REAL DEFAULT 0," +
                "has_end_home BOOLEAN DEFAULT 0," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";

        // 成员表，关联岛屿ID和玩家UUID，并带上权限级别
        String createMembersTable = "CREATE TABLE IF NOT EXISTS island_members (" +
                "island_id INTEGER," +
                "player_uuid VARCHAR(36)," +
                "role VARCHAR(16) DEFAULT 'MEMBER'," +
                "PRIMARY KEY (island_id, player_uuid)," +
                "FOREIGN KEY (island_id) REFERENCES islands(id) ON DELETE CASCADE" +
                ");";

        // 玩家数据表，记录玩家的岛屿删除次数等信息
        String createPlayerStatsTable = "CREATE TABLE IF NOT EXISTS player_stats (" +
                "player_uuid VARCHAR(36) PRIMARY KEY," +
                "delete_count INTEGER DEFAULT 0" +
                ");";

        // 岛屿权限表，存储自定义权限配置
        String createPermissionsTable = "CREATE TABLE IF NOT EXISTS island_permissions (" +
                "island_id INTEGER," +
                "role VARCHAR(16)," +
                "permission VARCHAR(64)," +
                "PRIMARY KEY (island_id, role, permission)," +
                "FOREIGN KEY (island_id) REFERENCES islands(id) ON DELETE CASCADE" +
                ");";

        // 玩家名称表，存储 UUID → 玩家名映射（用于显示和改名同步）
        String createPlayerNamesTable = "CREATE TABLE IF NOT EXISTS player_names (" +
                "player_uuid VARCHAR(36) PRIMARY KEY," +
                "player_name VARCHAR(16) NOT NULL," +
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createIslandsTable);
            stmt.execute(createMembersTable);
            stmt.execute(createPlayerStatsTable);
            stmt.execute(createPermissionsTable);
            stmt.execute(createPlayerNamesTable);
            migrateAddNameColumn();
            MessageUtil.consolePrint("&a数据库表结构检查/创建完毕。");
        } catch (SQLException e) {
            MessageUtil.consoleError("&c创建数据库表失败！");
            e.printStackTrace();
        }
    }

    private void migrateAddNameColumn() {
        try (Statement stmt = connection.createStatement()) {
            var rs = stmt.executeQuery("PRAGMA table_info(islands)");
            boolean hasNameColumn = false;
            while (rs.next()) {
                if ("name".equalsIgnoreCase(rs.getString("name"))) {
                    hasNameColumn = true;
                    break;
                }
            }
            rs.close();
            if (!hasNameColumn) {
                stmt.execute("ALTER TABLE islands ADD COLUMN name VARCHAR(64) DEFAULT ''");
                MessageUtil.consolePrint("&a数据库迁移：已添加 name 列到 islands 表。");
            }
        } catch (SQLException e) {
            MessageUtil.consoleError("&c数据库迁移检查 name 列失败！");
            e.printStackTrace();
        }
    }

    public void savePlayerName(UUID uuid, String playerName) {
        savePlayerName(uuid.toString(), playerName);
    }

    /**
     * 保存或更新玩家名称
     */
    public void savePlayerName(String uuid, String playerName) {
        String sql = "INSERT OR REPLACE INTO player_names (player_uuid, player_name, last_updated) VALUES (?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            pstmt.setString(2, playerName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            MessageUtil.consoleError("&c保存玩家名称失败！UUID: " + uuid);
            e.printStackTrace();
        }
    }

    /**
     * 获取玩家名称
     *
     * @return 玩家名称，如果不存在则返回 Optional.empty()
     */
    public Optional<String> getPlayerName(UUID uuid) {
        return getPlayerName(uuid.toString());
    }

    public Optional<String> getPlayerName(String uuid) {
        String sql = "SELECT player_name FROM player_names WHERE player_uuid = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("player_name"));
                }
            }
        } catch (SQLException e) {
            MessageUtil.consoleError("&c获取玩家名称失败！UUID: " + uuid);
            e.printStackTrace();
        }
        return Optional.empty();
    }

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
