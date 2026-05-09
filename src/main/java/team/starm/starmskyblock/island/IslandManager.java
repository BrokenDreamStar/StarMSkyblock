package team.starm.starmskyblock.island;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.PermissionConfigManager;
import team.starm.starmskyblock.database.SQLiteManager;
import team.starm.starmskyblock.grid.GridManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.setting.SettingsConfigManager;
import team.starm.starmskyblock.util.ColorUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class IslandManager {

    private final ConfigManager configManager;
    private final PermissionConfigManager permissionConfigManager;
    private final SettingsConfigManager settingsConfigManager;
    private final GridManager gridManager;
    private final SQLiteManager sqliteManager;
    private final Logger logger;

    private final Map<Integer, Island> islandsById = new HashMap<>();
    private final Map<UUID, Island> islandsByOwner = new HashMap<>();
    private int nextIslandId = 0; // 新建岛屿时分配的下一个螺旋网格ID

    public IslandManager(ConfigManager configManager, PermissionConfigManager permissionConfigManager,
            SettingsConfigManager settingsConfigManager, GridManager gridManager,
            SQLiteManager sqliteManager, Logger logger) {
        this.configManager = configManager;
        this.permissionConfigManager = permissionConfigManager;
        this.settingsConfigManager = settingsConfigManager;
        this.gridManager = gridManager;
        this.sqliteManager = sqliteManager;
        this.logger = logger;

        loadIslandsFromDatabase();
    }

    /**
     * 从数据库加载所有岛屿信息
     */
    private void loadIslandsFromDatabase() {
        String selectIslands = "SELECT id, owner_uuid, name, level, radius, center_x, center_z, custom_home_x, custom_home_y, custom_home_z, has_custom_home, permissions, settings FROM islands";
        String selectMembers = "SELECT player_uuid, role FROM island_members WHERE island_id = ?";

        try (Connection conn = sqliteManager.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(selectIslands)) {

            int maxId = -1;

            while (rs.next()) {
                int id = rs.getInt("id");
                UUID ownerId = UUID.fromString(rs.getString("owner_uuid"));
                String name = rs.getString("name");
                int radius = rs.getInt("radius");
                int centerX = rs.getInt("center_x");
                int centerZ = rs.getInt("center_z");

                Island island = new Island(id, ownerId, radius);
                island.setName(name);
                island.setCenterChunkX(centerX);
                island.setCenterChunkZ(centerZ);
                island.setMaxRadius(configManager.getIslandMaxRadius());
                island.setLevel(rs.getInt("level"));
                island.setSettingsFromJson(rs.getString("settings"));

                // 加载自定义权限最低等级（JSON）
                String permissionsJson = rs.getString("permissions");
                island.setPermissionsFromJson(permissionsJson);

                // 旧数据迁移：空 JSON 则从 config 初始化
                if (permissionsJson == null || permissionsJson.isEmpty() || permissionsJson.equals("{}")) {
                    permissionConfigManager.getDefaultMinLevels()
                            .forEach(island::setPermissionMinLevel);
                    savePermissionsToDb(island);
                }

                // 加载传送点（默认主世界）
                double customHomeX = rs.getDouble("custom_home_x");
                double customHomeY = rs.getDouble("custom_home_y");
                double customHomeZ = rs.getDouble("custom_home_z");
                boolean hasCustomHome = rs.getBoolean("has_custom_home");
                if (hasCustomHome) {
                    island.setCustomHome(Island.WorldType.NORMAL, customHomeX, customHomeY, customHomeZ);
                }

                // 加载成员
                try (PreparedStatement pstmt = conn.prepareStatement(selectMembers)) {
                    pstmt.setInt(1, id);
                    try (ResultSet memberRs = pstmt.executeQuery()) {
                        while (memberRs.next()) {
                            UUID memberUuid = UUID.fromString(memberRs.getString("player_uuid"));
                            IslandPermissionLevel role = IslandPermissionLevel.fromString(memberRs.getString("role"));
                            island.addMember(memberUuid, role);
                        }
                    }
                }

                islandsById.put(id, island);
                islandsByOwner.put(ownerId, island);

                if (id > maxId) {
                    maxId = id;
                }
            }

            // 更新 nextIslandId
            nextIslandId = maxId + 1;
            ColorUtil.consolePrint("&a成功从数据库加载了 " + islandsById.size() + " 个岛屿。");

        } catch (SQLException e) {
            ColorUtil.consoleError("&c加载岛屿数据失败！");
            e.printStackTrace();
        }
    }

    /**
     * 创建一个新岛屿
     *
     * @param ownerId     岛主 UUID
     * @param schematicId 结构文件ID
     * @param name        岛屿名称（可为null或空，则使用默认名称）
     * @return 新建的岛屿实例
     */
    public Island createIsland(UUID ownerId, String schematicId, String name) {
        if (islandsByOwner.containsKey(ownerId)) {
            throw new IllegalStateException("该玩家已经拥有一个岛屿！");
        }

        int islandId = nextIslandId++;
        int defaultRadius = configManager.getIslandRadius();

        Island island = new Island(islandId, ownerId, defaultRadius, schematicId, name);

        // 从 permissions.yml 初始化权限最低等级
        permissionConfigManager.getDefaultMinLevels()
                .forEach(island::setPermissionMinLevel);

        // 从 settings.yml 初始化岛屿默认设置
        island.applySettings(settingsConfigManager.getDefaultSettings());

        island.setMaxRadius(configManager.getIslandMaxRadius());

        // 获取该岛屿对应的中心区块坐标
        GridManager.GridLocation location = gridManager.getChunkLocation(islandId);
        island.setCenterChunkX(location.getChunkX());
        island.setCenterChunkZ(location.getChunkZ());

        // 保存岛主名称
        org.bukkit.entity.Player owner = org.bukkit.Bukkit.getPlayer(ownerId);
        if (owner != null) {
            sqliteManager.savePlayerName(ownerId, owner.getName());
        }

        // 保存到数据库
        String insertSql = "INSERT INTO islands (id, name, owner_uuid, level, radius, center_x, center_z, permissions, settings) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = sqliteManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            pstmt.setInt(1, islandId);
            pstmt.setString(2, island.getName());
            pstmt.setString(3, ownerId.toString());
            pstmt.setInt(4, island.getLevel());
            pstmt.setInt(5, defaultRadius);
            pstmt.setInt(6, island.getCenterChunkX());
            pstmt.setInt(7, island.getCenterChunkZ());
            pstmt.setString(8, island.getPermissionsJson());
            pstmt.setString(9, island.getSettingsJson());
            pstmt.executeUpdate();

            islandsById.put(islandId, island);
            islandsByOwner.put(ownerId, island);

            logger.info("岛屿已创建并保存至数据库！ID: " + islandId + "，中心区块坐标: " + location);
        } catch (SQLException e) {
            ColorUtil.consoleError("&c保存新岛屿到数据库失败！");
            e.printStackTrace();
            throw new RuntimeException("数据库错误，无法创建岛屿");
        }

        return island;
    }

    public Optional<Island> getIsland(int id) {
        return Optional.ofNullable(islandsById.get(id));
    }

    public Optional<Island> getIsland(UUID ownerId) {
        return Optional.ofNullable(islandsByOwner.get(ownerId));
    }

    /**
     * 根据玩家 UUID 获取其所属的岛屿（包括作为成员的情况）
     *
     * @param playerUuid 玩家 UUID
     * @return 岛屿实例（如果存在）
     */
    public Optional<Island> getIslandByPlayer(UUID playerUuid) {
        // 先检查是否为岛主
        Island island = islandsByOwner.get(playerUuid);
        if (island != null) {
            return Optional.of(island);
        }
        // 再检查是否为成员
        return islandsById.values().stream()
                .filter(i -> i.getMembers().containsKey(playerUuid))
                .findFirst();
    }

    /**
     * 根据玩家名获取岛屿
     *
     * @param playerName 玩家名
     * @return 岛屿实例（如果存在）
     */
    public Optional<Island> getIslandByPlayerName(String playerName) {
        org.bukkit.OfflinePlayer offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(playerName);
        if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
            return getIsland(offlinePlayer.getUniqueId());
        }
        return Optional.empty();
    }

    /**
     * 获取所有岛屿的集合
     *
     * @return 所有岛屿的集合
     */
    public java.util.Collection<Island> getAllIslands() {
        return islandsById.values();
    }

    /**
     * 检查玩家是否在自己的岛屿范围内
     *
     * @param player 玩家实例
     * @return 如果玩家在自己的岛屿范围内返回true，否则返回false
     */
    public boolean isPlayerOnOwnIsland(Player player) {
        Optional<Island> optionalIsland = getIsland(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            return false;
        }

        Island island = optionalIsland.get();
        return island.isChunkWithinIsland(
                player.getLocation().getChunk().getX(),
                player.getLocation().getChunk().getZ());
    }

    /**
     * 检查玩家是否在指定岛屿范围内
     *
     * @param player 玩家实例
     * @param island 要检查的岛屿
     * @return 如果玩家在指定岛屿范围内返回true，否则返回false
     */
    public boolean isPlayerOnIsland(Player player, Island island) {
        return island.isChunkWithinIsland(
                player.getLocation().getChunk().getX(),
                player.getLocation().getChunk().getZ());
    }

    /**
     * 更新岛屿的半径
     *
     * @param id        岛屿ID
     * @param newRadius 新的半径（区块单位）
     * @return 是否更新成功
     */
    public boolean updateIslandRadius(int id, int newRadius) {
        Island island = islandsById.get(id);
        if (island != null) {
            // 不能超过配置文件里规定的最大半径
            int maxRadius = configManager.getIslandMaxRadius();
            if (newRadius > maxRadius) {
                newRadius = maxRadius;
            }

            String updateSql = "UPDATE islands SET radius = ? WHERE id = ?";
            try (Connection conn = sqliteManager.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setInt(1, newRadius);
                pstmt.setInt(2, id);
                pstmt.executeUpdate();

                island.setRadius(newRadius);
                return true;
            } catch (SQLException e) {
                ColorUtil.consoleError("&c更新岛屿半径到数据库失败！ID: " + id);
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 更新岛屿名称
     *
     * @param id      岛屿ID
     * @param newName 新的岛屿名称
     * @return 是否更新成功
     */
    public boolean updateIslandName(int id, String newName) {
        Island island = islandsById.get(id);
        if (island != null) {
            String updateSql = "UPDATE islands SET name = ? WHERE id = ?";
            try (Connection conn = sqliteManager.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setString(1, newName != null ? newName : "");
                pstmt.setInt(2, id);
                pstmt.executeUpdate();

                island.setName(newName);
                return true;
            } catch (SQLException e) {
                ColorUtil.consoleError("&c更新岛屿名称到数据库失败！ID: " + id);
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 更新岛屿的自定义传送点
     *
     * @param id        岛屿ID
     * @param worldType 世界类型
     * @param x         传送点X坐标
     * @param y         传送点Y坐标
     * @param z         传送点Z坐标
     * @return 是否更新成功
     */
    public boolean updateIslandCustomHome(int id, Island.WorldType worldType, double x, double y, double z) {
        Island island = islandsById.get(id);
        if (island != null) {
            String sql = "UPDATE islands SET custom_home_x = ?, custom_home_y = ?, custom_home_z = ?, has_custom_home = ? WHERE id = ?";
            try (Connection conn = sqliteManager.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setDouble(1, x);
                pstmt.setDouble(2, y);
                pstmt.setDouble(3, z);
                pstmt.setBoolean(4, true);
                pstmt.setInt(5, id);
                pstmt.executeUpdate();

                island.setCustomHome(worldType, x, y, z);
                return true;
            } catch (SQLException e) {
                ColorUtil.consoleError("&c更新岛屿自定义传送点到数据库失败！ID: " + id);
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 清除岛屿的自定义传送点
     *
     * @param id 岛屿ID
     * @return 是否清除成功
     */
    public boolean clearIslandCustomHome(int id) {
        Island island = islandsById.get(id);
        if (island != null) {
            String sql = "UPDATE islands SET has_custom_home = ? WHERE id = ?";
            try (Connection conn = sqliteManager.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setBoolean(1, false);
                pstmt.setInt(2, id);
                pstmt.executeUpdate();

                island.clearCustomHome();
                return true;
            } catch (SQLException e) {
                ColorUtil.consoleError("&c清除岛屿自定义传送点到数据库失败！ID: " + id);
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 更新岛屿设置（JSON格式）
     */
    public boolean updateIslandSettings(int islandId, Island island) {
        Island stored = islandsById.get(islandId);
        if (stored != null) {
            String json = stored.getSettingsJson();
            String sql = "UPDATE islands SET settings = ? WHERE id = ?";
            try (Connection conn = sqliteManager.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, json);
                pstmt.setInt(2, islandId);
                pstmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                ColorUtil.consoleError("&c更新岛屿设置到数据库失败！ID: " + islandId);
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 获取玩家已删除岛屿的次数
     */
    public int getDeleteCount(UUID playerUuid) {
        String sql = "SELECT delete_count FROM player_stats WHERE player_uuid = ?";
        try (Connection conn = sqliteManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("delete_count");
                }
            }
        } catch (SQLException e) {
            ColorUtil.consoleError("&c获取玩家删除岛屿次数失败！UUID: " + playerUuid);
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 增加玩家已删除岛屿的次数
     */
    public void incrementDeleteCount(UUID playerUuid) {
        String sql = "INSERT INTO player_stats (player_uuid, delete_count) VALUES (?, 1) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET delete_count = delete_count + 1";
        try (Connection conn = sqliteManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            ColorUtil.consoleError("&c增加玩家删除岛屿次数失败！UUID: " + playerUuid);
            e.printStackTrace();
        }
    }

    /**
     * 添加成员到岛屿
     */
    public boolean addMemberToIsland(int islandId, UUID memberUuid, IslandPermissionLevel role) {
        Island island = islandsById.get(islandId);
        if (island != null) {
            String sql = "INSERT INTO island_members (island_id, player_uuid, role) VALUES (?, ?, ?)";
            try (Connection conn = sqliteManager.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, islandId);
                pstmt.setString(2, memberUuid.toString());
                pstmt.setString(3, role.name());
                pstmt.executeUpdate();

                // 保存成员名称
                org.bukkit.entity.Player member = org.bukkit.Bukkit.getPlayer(memberUuid);
                if (member != null) {
                    sqliteManager.savePlayerName(memberUuid, member.getName());
                }

                island.addMember(memberUuid, role);
                return true;
            } catch (SQLException e) {
                ColorUtil.consoleError("&c添加成员到岛屿失败！岛屿ID: " + islandId + ", 成员UUID: " + memberUuid);
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 从岛屿移除成员
     */
    public boolean removeMemberFromIsland(int islandId, UUID memberUuid) {
        Island island = islandsById.get(islandId);
        if (island != null) {
            String sql = "DELETE FROM island_members WHERE island_id = ? AND player_uuid = ?";
            try (Connection conn = sqliteManager.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, islandId);
                pstmt.setString(2, memberUuid.toString());
                pstmt.executeUpdate();

                island.removeMember(memberUuid);
                return true;
            } catch (SQLException e) {
                ColorUtil.consoleError("&c从岛屿移除成员失败！岛屿ID: " + islandId + ", 成员UUID: " + memberUuid);
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 更新成员角色
     */
    public boolean updateMemberRole(int islandId, UUID memberUuid, IslandPermissionLevel newRole) {
        Island island = islandsById.get(islandId);
        if (island != null && island.getMembers().containsKey(memberUuid)) {
            String sql = "UPDATE island_members SET role = ? WHERE island_id = ? AND player_uuid = ?";
            try (Connection conn = sqliteManager.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, newRole.name());
                pstmt.setInt(2, islandId);
                pstmt.setString(3, memberUuid.toString());
                pstmt.executeUpdate();

                island.setMemberRole(memberUuid, newRole);
                return true;
            } catch (SQLException e) {
                ColorUtil.consoleError("&c更新成员角色失败！岛屿ID: " + islandId + ", 成员UUID: " + memberUuid);
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 设置权限的最低等级（JSON 持久化）
     */
    public boolean setPermissionMinLevel(int islandId, IslandPermission permission, int minLevel) {
        Island island = islandsById.get(islandId);
        if (island != null) {
            island.setPermissionMinLevel(permission, minLevel);
            savePermissionsToDb(island);
            return true;
        }
        return false;
    }

    /**
     * 移除权限的最低等级设置（恢复为默认配置）
     */
    public boolean removePermissionMinLevel(int islandId, IslandPermission permission) {
        Island island = islandsById.get(islandId);
        if (island != null) {
            island.removePermissionMinLevel(permission);
            savePermissionsToDb(island);
            return true;
        }
        return false;
    }

    /**
     * 获取权限的最低等级
     */
    public Integer getPermissionMinLevel(int islandId, IslandPermission permission) {
        Island island = islandsById.get(islandId);
        if (island != null) {
            return island.getPermissionMinLevel(permission);
        }
        return null;
    }

    /**
     * 将岛屿的权限 JSON 保存到数据库
     */
    private void savePermissionsToDb(Island island) {
        String sql = "UPDATE islands SET permissions = ? WHERE id = ?";
        try (Connection conn = sqliteManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, island.getPermissionsJson());
            pstmt.setInt(2, island.getId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            ColorUtil.consoleError("&c保存权限数据失败！岛屿ID: " + island.getId());
            e.printStackTrace();
        }
    }

    /**
     * 获取玩家在指定岛屿的角色
     */
    public IslandPermissionLevel getPlayerRoleOnIsland(UUID playerUuid, int islandId) {
        Island island = islandsById.get(islandId);
        if (island != null) {
            return island.getMemberRole(playerUuid);
        }
        return IslandPermissionLevel.VISITOR;
    }

    // ==================== 岛屿区块查询 ====================

    /**
     * 根据区块坐标获取该位置所属的岛屿（使用岛屿当前半径）
     */
    public Optional<Island> getIslandAt(int chunkX, int chunkZ) {
        for (Island island : islandsById.values()) {
            if (island.isChunkWithinIsland(chunkX, chunkZ)) {
                return Optional.of(island);
            }
        }
        return Optional.empty();
    }

    /**
     * 根据区块坐标获取该位置所属的岛屿（使用岛屿最大半径）
     */
    public Optional<Island> getIslandAtMaxRange(int chunkX, int chunkZ) {
        for (Island island : islandsById.values()) {
            if (island.isChunkWithinMaxRange(chunkX, chunkZ)) {
                return Optional.of(island);
            }
        }
        return Optional.empty();
    }

    /**
     * 从内存中安全移除岛屿数据（不删除数据库记录）
     * 用于异步删除操作中，先移除内存数据以防止玩家访问
     */
    public void removeIslandFromMemory(Island island) {
        islandsById.remove(island.getId());
        islandsByOwner.remove(island.getOwnerId());
    }

    /**
     * 仅删除数据库中的岛屿记录（不操作内存数据）
     * 用于异步删除操作中，内存数据已提前移除的情况
     */
    public boolean deleteIslandFromDatabase(Island island) {
        String deleteMembersSql = "DELETE FROM island_members WHERE island_id = ?";
        String deleteIslandSql = "DELETE FROM islands WHERE id = ?";
        try (Connection conn = sqliteManager.getConnection()) {
            // 删除成员记录
            try (PreparedStatement pstmt = conn.prepareStatement(deleteMembersSql)) {
                pstmt.setInt(1, island.getId());
                pstmt.executeUpdate();
            }

            // 删除岛屿记录
            try (PreparedStatement pstmt = conn.prepareStatement(deleteIslandSql)) {
                pstmt.setInt(1, island.getId());
                pstmt.executeUpdate();
            }

            return true;
        } catch (SQLException e) {
            ColorUtil.consoleError("&c从数据库删除岛屿失败！ID: " + island.getId());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 删除岛屿数据及内存记录
     */
    public boolean deleteIsland(Island island) {
        String deleteMembersSql = "DELETE FROM island_members WHERE island_id = ?";
        String deleteIslandSql = "DELETE FROM islands WHERE id = ?";
        try (Connection conn = sqliteManager.getConnection()) {
            // 删除成员记录
            try (PreparedStatement pstmt = conn.prepareStatement(deleteMembersSql)) {
                pstmt.setInt(1, island.getId());
                pstmt.executeUpdate();
            }

            // 删除岛屿记录
            try (PreparedStatement pstmt = conn.prepareStatement(deleteIslandSql)) {
                pstmt.setInt(1, island.getId());
                pstmt.executeUpdate();
            }

            // 从内存中移除
            islandsById.remove(island.getId());
            islandsByOwner.remove(island.getOwnerId());

            return true;
        } catch (SQLException e) {
            ColorUtil.consoleError("&c从数据库删除岛屿失败！ID: " + island.getId());
            e.printStackTrace();
        }
        return false;
    }
}
