package team.starm.starmskyblock.database;

import team.starm.starmskyblock.message.MessageUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 玩家数据访问层 —— 管理玩家名称、边界开关、下界标记的持久化与缓存。
 */
public class PlayerRepository {

    private final SQLiteManager sqliteManager;
    private final Object dbLock;

    private final Map<UUID, String> playerNameCache = createBoundedCache(2000);
    private final Map<UUID, Boolean> firstNetherJoinCache = createBoundedCache(2000);

    public PlayerRepository(SQLiteManager sqliteManager) {
        this.sqliteManager = sqliteManager;
        this.dbLock = sqliteManager.getDbLock();
    }

    // ==================== 缓存预热 ====================

    public void warmUpPlayerNameCache() {
        String sql = "SELECT player_uuid, player_name FROM players";
        synchronized (dbLock) {
            Connection conn = sqliteManager.getConnection();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                int count = 0;
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    String name = rs.getString("player_name");
                    playerNameCache.put(uuid, name);
                    count++;
                }
                MessageUtil.consolePrint("已预加载 " + count + " 个玩家名称到缓存");
            } catch (SQLException e) {
                MessageUtil.consoleError("预加载玩家名称缓存失败！");
                e.printStackTrace();
            }
        }
    }

    // ==================== 玩家名称 ====================

    public void savePlayerName(UUID uuid, String playerName) {
        String sql = "INSERT INTO players (player_uuid, player_name) VALUES (?, ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET player_name = excluded.player_name";
        synchronized (dbLock) {
            playerNameCache.put(uuid, playerName);
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, playerName);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("保存玩家名称失败！UUID: " + uuid);
                e.printStackTrace();
            }
        }
    }

    public Optional<String> getPlayerName(UUID uuid) {
        synchronized (dbLock) {
            String cached = playerNameCache.get(uuid);
            if (cached != null) {
                return Optional.of(cached);
            }
        }
        String sql = "SELECT player_name FROM players WHERE player_uuid = ?";
        synchronized (dbLock) {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String name = rs.getString("player_name");
                        playerNameCache.put(uuid, name);
                        return Optional.of(name);
                    }
                }
            } catch (SQLException e) {
                MessageUtil.consoleError("获取玩家名称失败！UUID: " + uuid);
                e.printStackTrace();
            }
        }
        return Optional.empty();
    }

    public void invalidatePlayerNameCache(UUID uuid) {
        playerNameCache.remove(uuid);
    }

    // ==================== 边界开关 ====================

    public boolean isBorderEnabled(UUID playerUuid) {
        String sql = "SELECT border_enabled FROM players WHERE player_uuid = ?";
        synchronized (dbLock) {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean("border_enabled");
                    }
                }
            } catch (SQLException e) {
                MessageUtil.consoleError("获取边界开关状态失败！UUID: " + playerUuid);
                e.printStackTrace();
            }
        }
        return true;
    }

    public void setBorderEnabled(UUID playerUuid, boolean enabled) {
        String sql = "INSERT INTO players (player_uuid, player_name, border_enabled) VALUES (?, '', ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET border_enabled = excluded.border_enabled";
        synchronized (dbLock) {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                pstmt.setBoolean(2, enabled);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("保存边界开关状态失败！UUID: " + playerUuid);
                e.printStackTrace();
            }
        }
    }

    // ==================== 首次下界 ====================

    public boolean isFirstNetherJoin(UUID playerUuid) {
        synchronized (dbLock) {
            Boolean cached = firstNetherJoinCache.get(playerUuid);
            if (cached != null) {
                return cached;
            }
        }
        String sql = "SELECT first_nether_join FROM players WHERE player_uuid = ?";
        synchronized (dbLock) {
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        boolean value = rs.getBoolean("first_nether_join");
                        firstNetherJoinCache.put(playerUuid, value);
                        return value;
                    }
                }
            } catch (SQLException e) {
                MessageUtil.consoleError("获取首次进入下界状态失败！UUID: " + playerUuid);
                e.printStackTrace();
            }
            firstNetherJoinCache.put(playerUuid, true);
            return true;
        }
    }

    public void setFirstNetherJoin(UUID playerUuid, boolean firstJoin) {
        String sql = "INSERT INTO players (player_uuid, player_name, first_nether_join) VALUES (?, '', ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET first_nether_join = excluded.first_nether_join";
        synchronized (dbLock) {
            firstNetherJoinCache.put(playerUuid, firstJoin);
            Connection conn = sqliteManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                pstmt.setBoolean(2, firstJoin);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                MessageUtil.consoleError("保存首次进入下界状态失败！UUID: " + playerUuid);
                e.printStackTrace();
            }
        }
    }

    public void invalidateFirstNetherJoinCache(UUID uuid) {
        firstNetherJoinCache.remove(uuid);
    }

    // ==================== 缓存管理 ====================

    public void clearCaches() {
        playerNameCache.clear();
        firstNetherJoinCache.clear();
    }

    // ==================== LRU 缓存工厂 ====================

    private static <K, V> Map<K, V> createBoundedCache(int maxSize) {
        return new LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        };
    }
}
