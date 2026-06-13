package team.starm.starmskyblock.database;

import team.starm.starmskyblock.message.MessageUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 岛屿数据访问层 —— 封装所有岛屿相关的数据库操作。
 * 每个方法内部自行管理 {@code ReentrantReadWriteLock} 同步。
 */
public class IslandRepository {

    private final SQLiteManager sqliteManager;
    private final java.util.concurrent.locks.ReentrantReadWriteLock dbLock;

    public IslandRepository(SQLiteManager sqliteManager) {
        this.sqliteManager = sqliteManager;
        this.dbLock = sqliteManager.getDbLock();
    }

    // ==================== 数据行记录（仅用于加载路径） ====================

    public record IslandRow(int id, UUID ownerUuid, String name, int level, double totalExperience,
                            String blockCounts, int radius,
                            int centerX, int centerZ, String permissions, String settings,
                            String homeData, String createdAt, boolean netherUnlocked,
                            int generatorLevel, String generatorDisabled,
                            double baselineExperience, String baselineBlockCounts) {}

    public record MemberRow(int islandId, UUID playerUuid, String role) {}

    public record CoopRow(int islandId, UUID playerUuid) {}

    // ==================== 批量加载 ====================

    public List<IslandRow> loadAllIslands() {
        String sql = "SELECT id, owner_uuid, name, level, COALESCE(total_points, 0) as total_points, COALESCE(block_counts, '{}') as block_counts, radius, center_x, center_z, permissions, settings, home_data, created_at, nether_unlocked, generator_level, generator_disabled, COALESCE(baseline_total_points, 0) as baseline_total_points, COALESCE(baseline_block_counts, '{}') as baseline_block_counts FROM islands";
        List<IslandRow> rows = new ArrayList<>();
        dbLock.readLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    try {
                        rows.add(new IslandRow(
                                rs.getInt("id"),
                                UUID.fromString(rs.getString("owner_uuid")),
                                rs.getString("name"),
                                rs.getInt("level"),
                                rs.getDouble("total_points"),
                                rs.getString("block_counts"),
                                rs.getInt("radius"),
                                rs.getInt("center_x"),
                                rs.getInt("center_z"),
                                rs.getString("permissions"),
                                rs.getString("settings"),
                                rs.getString("home_data"),
                                rs.getString("created_at"),
                                rs.getInt("nether_unlocked") == 1,
                                rs.getInt("generator_level"),
                                rs.getString("generator_disabled"),
                                rs.getDouble("baseline_total_points"),
                                rs.getString("baseline_block_counts")
                        ));
                    } catch (IllegalArgumentException e) {
                        MessageUtil.consoleWarn("跳过无效 UUID 的岛屿行，id=" + rs.getInt("id"));
                    }
                }
            } catch (SQLException e) {
                MessageUtil.consoleError("加载岛屿数据失败！", e);
            }
        } finally {
            dbLock.readLock().unlock();
        }
        return rows;
    }

    public List<MemberRow> loadAllMembers() {
        String sql = "SELECT island_id, player_uuid, role FROM island_members";
        List<MemberRow> rows = new ArrayList<>();
        dbLock.readLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    try {
                        rows.add(new MemberRow(
                                rs.getInt("island_id"),
                                UUID.fromString(rs.getString("player_uuid")),
                                rs.getString("role")
                        ));
                    } catch (IllegalArgumentException e) {
                        MessageUtil.consoleWarn("跳过无效 UUID 的成员行，island_id=" + rs.getInt("island_id"));
                    }
                }
            } catch (SQLException e) {
                MessageUtil.consoleError("加载岛屿成员数据失败！", e);
            }
        } finally {
            dbLock.readLock().unlock();
        }
        return rows;
    }

    public List<CoopRow> loadAllCoops() {
        String sql = "SELECT island_id, player_uuid FROM island_coops";
        List<CoopRow> rows = new ArrayList<>();
        dbLock.readLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    try {
                        rows.add(new CoopRow(
                                rs.getInt("island_id"),
                                UUID.fromString(rs.getString("player_uuid"))
                        ));
                    } catch (IllegalArgumentException e) {
                        MessageUtil.consoleWarn("跳过无效 UUID 的合作者行，island_id=" + rs.getInt("island_id"));
                    }
                }
            } catch (SQLException e) {
                MessageUtil.consoleError("加载岛屿合作者数据失败！", e);
            }
        } finally {
            dbLock.readLock().unlock();
        }
        return rows;
    }

    // ==================== 岛屿 CRUD ====================

    public void insertIsland(int id, String name, UUID ownerUuid, int level, int radius,
                             int centerX, int centerZ, String permissionsJson, String settingsJson) {
        insertIsland(id, name, ownerUuid, level, radius, centerX, centerZ, permissionsJson, settingsJson, 1);
    }

    public void insertIsland(int id, String name, UUID ownerUuid, int level, int radius,
                             int centerX, int centerZ, String permissionsJson, String settingsJson,
                             int generatorLevel) {
        String sql = "INSERT INTO islands (id, name, owner_uuid, level, radius, center_x, center_z, permissions, settings, generator_level, generator_disabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, id);
                pstmt.setString(2, name);
                pstmt.setString(3, ownerUuid.toString());
                pstmt.setInt(4, level);
                pstmt.setInt(5, radius);
                pstmt.setInt(6, centerX);
                pstmt.setInt(7, centerZ);
                pstmt.setString(8, permissionsJson);
                pstmt.setString(9, settingsJson);
                pstmt.setInt(10, generatorLevel);
                pstmt.setString(11, "{}");
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("保存新岛屿到数据库失败！", e);
                throw new RuntimeException("数据库错误，无法创建岛屿");
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public void updateRadius(int id, int newRadius) {
        String sql = "UPDATE islands SET radius = ? WHERE id = ?";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, newRadius);
                pstmt.setInt(2, id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("更新岛屿半径到数据库失败！ID: " + id, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public void updateNetherUnlocked(int id, boolean unlocked) {
        String sql = "UPDATE islands SET nether_unlocked = ? WHERE id = ?";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, unlocked ? 1 : 0);
                pstmt.setInt(2, id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("更新岛屿下界解锁状态到数据库失败！ID: " + id, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public void updateName(int id, String newName) {
        String sql = "UPDATE islands SET name = ? WHERE id = ?";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, newName != null ? newName : "");
                pstmt.setInt(2, id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("更新岛屿名称到数据库失败！ID: " + id, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public void updateGeneratorLevel(int id, int generatorLevel) {
        String sql = "UPDATE islands SET generator_level = ? WHERE id = ?";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, generatorLevel);
                pstmt.setInt(2, id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("更新岛屿刷石机等级到数据库失败！ID: " + id, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public void updateGeneratorDisabled(int id, String json) {
        String sql = "UPDATE islands SET generator_disabled = ? WHERE id = ?";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, json);
                pstmt.setInt(2, id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("更新岛屿刷石机禁用矿石到数据库失败！ID: " + id, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public void updateHomeData(int id, String json) {
        String sql = "UPDATE islands SET home_data = ? WHERE id = ?";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, json);
                pstmt.setInt(2, id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("更新岛屿自定义传送点到数据库失败！ID: " + id, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public void clearHomeData(int id) {
        String sql = "UPDATE islands SET home_data = '{}' WHERE id = ?";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("清除岛屿自定义传送点到数据库失败！ID: " + id, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public void updateSettings(int id, String json) {
        String sql = "UPDATE islands SET settings = ? WHERE id = ?";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, json);
                pstmt.setInt(2, id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("更新岛屿设置到数据库失败！ID: " + id, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public void savePermissions(int id, String json) {
        String sql = "UPDATE islands SET permissions = ? WHERE id = ?";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, json);
                pstmt.setInt(2, id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("保存权限数据失败！岛屿ID: " + id, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public void batchUpdateIsland(int id, String name, int radius, int generatorLevel, String generatorDisabledJson) {
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "UPDATE islands SET name=?, radius=?, generator_level=?, generator_disabled=? WHERE id=?")) {
                pstmt.setString(1, name != null ? name : "");
                pstmt.setInt(2, radius);
                pstmt.setInt(3, generatorLevel);
                pstmt.setString(4, generatorDisabledJson);
                pstmt.setInt(5, id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("批量更新岛屿数据失败！ID: " + id, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    // ==================== 成员操作 ====================

    public void addMember(int islandId, UUID memberUuid, String role) {
        String sql = "INSERT INTO island_members (island_id, player_uuid, role) VALUES (?, ?, ?)";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, islandId);
                pstmt.setString(2, memberUuid.toString());
                pstmt.setString(3, role);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("添加成员到岛屿失败！岛屿ID: " + islandId + ", 成员UUID: " + memberUuid, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public void deleteCoopsForIsland(Connection conn, int islandId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "DELETE FROM island_coops WHERE island_id = ?")) {
            pstmt.setInt(1, islandId);
            pstmt.executeUpdate();
        }
    }

    public void deleteCoop(int islandId, UUID playerUuid) {
        String sql = "DELETE FROM island_coops WHERE island_id = ? AND player_uuid = ?";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, islandId);
                pstmt.setString(2, playerUuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("删除合作者失败！岛屿ID: " + islandId + ", 玩家UUID: " + playerUuid, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public void addCoop(int islandId, UUID playerUuid) {
        String sql = "INSERT INTO island_coops (island_id, player_uuid) VALUES (?, ?)";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, islandId);
                pstmt.setString(2, playerUuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("添加合作者到岛屿失败！岛屿ID: " + islandId + ", 玩家UUID: " + playerUuid, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public void removeMember(int islandId, UUID memberUuid) {
        String sql = "DELETE FROM island_members WHERE island_id = ? AND player_uuid = ?";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, islandId);
                pstmt.setString(2, memberUuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("从岛屿移除成员失败！岛屿ID: " + islandId + ", 成员UUID: " + memberUuid, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public void updateMemberRole(int islandId, UUID memberUuid, String newRole) {
        String sql = "UPDATE island_members SET role = ? WHERE island_id = ? AND player_uuid = ?";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, newRole);
                pstmt.setInt(2, islandId);
                pstmt.setString(3, memberUuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("更新成员权限组失败！岛屿ID: " + islandId + ", 成员UUID: " + memberUuid, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    /**
     * 在事务中删除岛屿的所有关联数据（合作者 → 成员 → 岛屿本身）。
     * 调用方需自行清理内存索引。
     */
    public void deleteIslandCascade(int islandId) {
        try {
            sqliteManager.executeInTransaction(conn -> {
                deleteCoopsForIsland(conn, islandId);
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "DELETE FROM island_members WHERE island_id = ?")) {
                    pstmt.setInt(1, islandId);
                    pstmt.executeUpdate();
                }
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "DELETE FROM islands WHERE id = ?")) {
                    pstmt.setInt(1, islandId);
                    pstmt.executeUpdate();
                }
                return null;
            });
        } catch (SQLException e) {
            MessageUtil.consoleError("从数据库删除岛屿失败！ID: " + islandId, e);
        }
    }

    // ==================== 成员→合作者迁移（事务内） ====================

    /**
     * 在同一个事务中：删除指定合作者记录 + 插入成员记录。
     * 用于将合作者提升为成员时保证原子性。
     */
    public void migrateCoopToMember(int islandId, UUID playerUuid, String role) {
        try {
            sqliteManager.executeInTransaction(conn -> {
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "DELETE FROM island_coops WHERE island_id = ? AND player_uuid = ?")) {
                    pstmt.setInt(1, islandId);
                    pstmt.setString(2, playerUuid.toString());
                    pstmt.executeUpdate();
                }
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "INSERT INTO island_members (island_id, player_uuid, role) VALUES (?, ?, ?)")) {
                    pstmt.setInt(1, islandId);
                    pstmt.setString(2, playerUuid.toString());
                    pstmt.setString(3, role);
                    pstmt.executeUpdate();
                }
                return null;
            });
        } catch (SQLException e) {
            MessageUtil.consoleError("合作者转成员失败！岛屿ID: " + islandId + ", 玩家UUID: " + playerUuid, e);
        }
    }

    // ==================== 玩家统计 ====================

    public int getDeleteCount(UUID playerUuid) {
        String sql = "SELECT delete_count FROM player_stats WHERE player_uuid = ?";
        dbLock.readLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("delete_count");
                    }
                }
            } catch (SQLException e) {
                MessageUtil.consoleError("获取玩家删除岛屿次数失败！UUID: " + playerUuid, e);
            }
        } finally {
            dbLock.readLock().unlock();
        }
        return 0;
    }

    public void incrementDeleteCount(UUID playerUuid) {
        String sql = "INSERT INTO player_stats (player_uuid, delete_count) VALUES (?, 1) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET delete_count = delete_count + 1";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("增加玩家删除岛屿次数失败！UUID: " + playerUuid, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    // ==================== 等级系统 ====================

    /**
     * 更新岛屿模板基线（方块计数 + 总分）
     */
    public void updateBaseline(int id, double baselineExperience, String baselineBlockCountsJson) {
        String sql = "UPDATE islands SET baseline_total_points = ?, baseline_block_counts = ? WHERE id = ?";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setDouble(1, baselineExperience);
                pstmt.setString(2, baselineBlockCountsJson != null ? baselineBlockCountsJson : "{}");
                pstmt.setInt(3, id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("更新岛屿模板基线到数据库失败！ID: " + id, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    /**
     * 更新岛屿等级、总分和方块计数
     */
    public void updateLevel(int id, int level, double totalExperience, String blockCountsJson) {
        String sql = "UPDATE islands SET level = ?, total_points = ?, block_counts = ? WHERE id = ?";
        dbLock.writeLock().lock();
        try {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, level);
                pstmt.setDouble(2, totalExperience);
                pstmt.setString(3, blockCountsJson != null ? blockCountsJson : "{}");
                pstmt.setInt(4, id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("更新岛屿等级到数据库失败！ID: " + id, e);
            }
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public SQLiteManager getSqliteManager() {
        return sqliteManager;
    }
}
