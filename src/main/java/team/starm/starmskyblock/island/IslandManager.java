package team.starm.starmskyblock.island;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.PermissionConfigManager;
import team.starm.starmskyblock.config.SettingsConfigManager;
import team.starm.starmskyblock.database.IslandRepository;
import team.starm.starmskyblock.database.PlayerRepository;
import team.starm.starmskyblock.grid.GridManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 岛屿管理器 —— 核心业务层，负责岛屿的 CRUD、成员管理、区块查询和权限持久化。
 * <p>
 * 维护多级内存索引以支持 O(1) 查询：
 * <ul>
 *   <li>islandsById / islandsByOwner — 主键和岛主索引</li>
 *   <li>islandGridIndex — 网格空间索引（区块 → 岛屿）</li>
 *   <li>memberToIslandIndex — 成员反向索引（玩家 → 岛屿）</li>
 *   <li>coopToIslandsIndex — 合作者反向索引（玩家 → 岛屿列表）</li>
 * </ul>
 * 所有数据库写操作委托给 IslandRepository。
 */
public class IslandManager {

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    private final ConfigManager configManager;
    private final PermissionConfigManager permissionConfigManager;
    private final SettingsConfigManager settingsConfigManager;
    private final GridManager gridManager;
    private final IslandRepository islandRepo;
    private final PlayerRepository playerRepo;

    /** 岛屿主索引（ID → 岛屿） */
    private final Map<Integer, Island> islandsById = new ConcurrentHashMap<>();
    /** 岛主索引（岛主 UUID → 岛屿） */
    private final Map<UUID, Island> islandsByOwner = new ConcurrentHashMap<>();
    /** 网格空间索引：grid cell 编码键 → 岛屿 ID，实现 O(1) 区块→岛屿查询 */
    private final Map<Long, Integer> islandGridIndex = new ConcurrentHashMap<>();
    /** 成员反向索引：玩家 UUID → 所在岛屿 ID */
    private final Map<UUID, Integer> memberToIslandIndex = new ConcurrentHashMap<>();
    /** 合作者反向索引：玩家 UUID → 可访问的岛屿 ID 列表 */
    private final Map<UUID, java.util.List<Integer>> coopToIslandsIndex = new ConcurrentHashMap<>();
    /** 删除次数缓存（玩家 UUID → 已删除次数），避免频繁查库 */
    private final Map<UUID, Integer> deleteCountCache = new ConcurrentHashMap<>();
    /** 下一个可用岛屿 ID（自增） */
    private int nextIslandId = 0;

    public IslandManager(ConfigManager configManager, PermissionConfigManager permissionConfigManager,
            SettingsConfigManager settingsConfigManager, GridManager gridManager,
            IslandRepository islandRepo, PlayerRepository playerRepo) {
        this.configManager = configManager;
        this.permissionConfigManager = permissionConfigManager;
        this.settingsConfigManager = settingsConfigManager;
        this.gridManager = gridManager;
        this.islandRepo = islandRepo;
        this.playerRepo = playerRepo;

        loadIslandsFromDatabase();
    }

    /**
     * 从数据库批量加载所有岛屿、成员和合作者数据到内存索引。
     * 使用 IslandRepository 的三条批量查询分别读取，避免 N+1 查询问题。
     */
    private void loadIslandsFromDatabase() {
        int maxId = -1;

        for (IslandRepository.IslandRow row : islandRepo.loadAllIslands()) {
            int id = row.id();
            Island island = new Island(id, row.ownerUuid(), row.radius());
            island.setName(row.name());
            island.setCenterChunkX(row.centerX());
            island.setCenterChunkZ(row.centerZ());
            island.setMaxRadius(configManager.getIslandMaxRadius());
            island.setLevel(row.level());
            island.setGeneratorLevel(row.generatorLevel());
            island.setDisabledGeneratorOresFromJson(row.generatorDisabled());
            island.setCreatedAt(row.createdAt());
            island.setNetherUnlocked(row.netherUnlocked());
            island.setSettingsFromJson(row.settings());

            String permissionsJson = row.permissions();
            island.setPermissionsFromJson(permissionsJson);

            if (permissionsJson == null || permissionsJson.isEmpty() || permissionsJson.equals("{}")) {
                permissionConfigManager.getDefaultMinLevels()
                        .forEach(island::setPermissionMinLevel);
                islandRepo.savePermissions(id, island.getPermissionsJson());
            }

            String homeData = row.homeData();
            island.setHomeFromJson(homeData);
            if (homeData != null && !homeData.isEmpty() && !homeData.equals("{}")) {
                try {
                    var homeMap = GSON.fromJson(homeData, java.util.Map.class);
                    if (homeMap != null && homeMap.containsKey("world")) {
                        Island.WorldType worldType = Island.WorldType.valueOf((String) homeMap.get("world"));
                        double hx = ((Number) homeMap.get("x")).doubleValue();
                        double hy = ((Number) homeMap.get("y")).doubleValue();
                        double hz = ((Number) homeMap.get("z")).doubleValue();
                        island.setCustomHome(worldType, hx, hy, hz);
                    }
                } catch (Exception ignored) {}
            }

            islandsById.put(id, island);
            islandsByOwner.put(row.ownerUuid(), island);
            memberToIslandIndex.put(row.ownerUuid(), id);
            addToGridIndex(island);

            if (id > maxId) {
                maxId = id;
            }
        }

        nextIslandId = maxId + 1;

        for (IslandRepository.MemberRow row : islandRepo.loadAllMembers()) {
            Island island = islandsById.get(row.islandId());
            if (island != null) {
                UUID memberUuid = row.playerUuid();
                IslandPermissionLevel role = IslandPermissionLevel.fromString(row.role());
                island.addMember(memberUuid, role);
                memberToIslandIndex.put(memberUuid, row.islandId());
            }
        }

        for (IslandRepository.CoopRow row : islandRepo.loadAllCoops()) {
            Island island = islandsById.get(row.islandId());
            if (island != null) {
                UUID coopUuid = row.playerUuid();
                island.addCoop(coopUuid);
                coopToIslandsIndex.computeIfAbsent(coopUuid,
                        k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>())).add(row.islandId());
            }
        }

        MessageUtil.consolePrint("成功从数据库加载了 " + islandsById.size() + " 个岛屿。");
    }

    public Island createIsland(UUID ownerId, String schematicId, String name) {
        if (islandsByOwner.containsKey(ownerId)) {
            throw new IllegalStateException("该玩家已经拥有一个岛屿！");
        }

        int islandId = nextIslandId++;
        int defaultRadius = configManager.getIslandRadius();

        Island island = new Island(islandId, ownerId, defaultRadius, schematicId, name);

        permissionConfigManager.getDefaultMinLevels()
                .forEach(island::setPermissionMinLevel);

        island.applySettings(settingsConfigManager.getDefaultSettings());

        island.setMaxRadius(configManager.getIslandMaxRadius());

        GridManager.GridLocation location = gridManager.getChunkLocation(islandId);
        island.setCenterChunkX(location.chunkX());
        island.setCenterChunkZ(location.chunkZ());

        Player owner = org.bukkit.Bukkit.getPlayer(ownerId);
        if (owner != null) {
            playerRepo.savePlayerName(ownerId, owner.getName());
        }

        islandRepo.insertIsland(islandId, island.getName(), ownerId, island.getLevel(),
                defaultRadius, island.getCenterChunkX(), island.getCenterChunkZ(),
                island.getPermissionsJson(), island.getSettingsJson(),
                island.getGeneratorLevel());

        island.setCreatedAt(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date()));

        islandsById.put(islandId, island);
        islandsByOwner.put(ownerId, island);
        memberToIslandIndex.put(ownerId, islandId);
        addToGridIndex(island);

        String playerName = (owner != null) ? owner.getName() : ownerId.toString();
        MessageUtil.consolePrint("玩家" + playerName + "已创建岛屿 ID:" + islandId + " 中心区块坐标: " + location);

        return island;
    }

    /** 根据岛屿 ID 查询岛屿 */
    public Optional<Island> getIsland(int id) {
        return Optional.ofNullable(islandsById.get(id));
    }

    /** 根据岛主 UUID 查询岛屿 */
    public Optional<Island> getIsland(UUID ownerId) {
        return Optional.ofNullable(islandsByOwner.get(ownerId));
    }

    /**
     * 根据玩家 UUID 查询其所在的岛屿（先查岛主，再查成员）。
     * 合作者不视为"所在"岛屿，需通过 getIslandsByCoop 查询。
     */
    public Optional<Island> getIslandByPlayer(UUID playerUuid) {
        Island island = islandsByOwner.get(playerUuid);
        if (island != null) {
            return Optional.of(island);
        }
        Integer islandId = memberToIslandIndex.get(playerUuid);
        if (islandId != null) {
            return Optional.ofNullable(islandsById.get(islandId));
        }
        return Optional.empty();
    }

    /** 查询某玩家以合作者身份可访问的所有岛屿列表 */
    public java.util.List<Island> getIslandsByCoop(UUID playerUuid) {
        java.util.List<Integer> ids = coopToIslandsIndex.get(playerUuid);
        if (ids == null || ids.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<Island> result = new java.util.ArrayList<>(ids.size());
        for (Integer id : ids) {
            Island island = islandsById.get(id);
            if (island != null) {
                result.add(island);
            }
        }
        return result;
    }

    /** 根据玩家名称查询其岛屿（离线兼容） */
    @SuppressWarnings("deprecation")
    public Optional<Island> getIslandByPlayerName(String playerName) {
        org.bukkit.OfflinePlayer offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(playerName);
        if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
            return getIsland(offlinePlayer.getUniqueId());
        }
        return Optional.empty();
    }

    /** 根据岛屿名称查询岛屿（名称不区分大小写，返回第一个匹配项） */
    public Optional<Island> getIslandByName(String islandName) {
        for (Island island : islandsById.values()) {
            if (island.getName() != null && island.getName().equalsIgnoreCase(islandName)) {
                return Optional.of(island);
            }
        }
        return Optional.empty();
    }

    /** 根据岛屿名称查询所有匹配的岛屿（可能存在同名岛屿） */
    public java.util.List<Island> getIslandsByName(String islandName) {
        java.util.List<Island> result = new java.util.ArrayList<>();
        for (Island island : islandsById.values()) {
            if (island.getName() != null && island.getName().equalsIgnoreCase(islandName)) {
                result.add(island);
            }
        }
        return result;
    }

    /** 返回所有已加载的岛屿集合 */
    public java.util.Collection<Island> getAllIslands() {
        return islandsById.values();
    }

    /** 判断某玩家当前是否在自己的岛屿范围内 */
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

    /** 判断某玩家当前是否在指定岛屿范围内 */
    public boolean isPlayerOnIsland(Player player, Island island) {
        return island.isChunkWithinIsland(
                player.getLocation().getChunk().getX(),
                player.getLocation().getChunk().getZ());
    }

    /** 更新岛屿半径（不超过配置的最大值），同步写库 */
    public boolean updateIslandRadius(int id, int newRadius) {
        Island island = islandsById.get(id);
        if (island != null) {
            int maxRadius = configManager.getIslandMaxRadius();
            if (newRadius > maxRadius) {
                newRadius = maxRadius;
            }

            islandRepo.updateRadius(id, newRadius);
            island.setRadius(newRadius);
            return true;
        }
        return false;
    }

    /** 更新岛屿下界解锁状态，同步写库 */
    public boolean updateIslandNetherUnlocked(int id, boolean unlocked) {
        Island island = islandsById.get(id);
        if (island != null) {
            islandRepo.updateNetherUnlocked(id, unlocked);
            island.setNetherUnlocked(unlocked);
            return true;
        }
        return false;
    }

    /** 更新岛屿名称，同步写库 */
    public boolean updateIslandName(int id, String newName) {
        Island island = islandsById.get(id);
        if (island != null) {
            islandRepo.updateName(id, newName);
            island.setName(newName);
            return true;
        }
        return false;
    }

    /** 更新岛屿自定义传送点，同步写库 */
    public boolean updateIslandCustomHome(int id, Island.WorldType worldType, double x, double y, double z) {
        Island island = islandsById.get(id);
        if (island != null) {
            String json = "{\"world\":\"" + worldType.name() + "\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + "}";
            islandRepo.updateHomeData(id, json);
            island.setCustomHome(worldType, x, y, z);
            return true;
        }
        return false;
    }

    /** 清除岛屿自定义传送点（恢复为默认 spawn 逻辑） */
    public boolean clearIslandCustomHome(int id) {
        Island island = islandsById.get(id);
        if (island != null) {
            islandRepo.clearHomeData(id);
            island.clearCustomHome();
            return true;
        }
        return false;
    }

    /** 更新岛屿设置（PVP、生成等开关），同步写库 */
    public boolean updateIslandSettings(int islandId, Island island) {
        Island stored = islandsById.get(islandId);
        if (stored != null) {
            islandRepo.updateSettings(islandId, stored.getSettingsJson());
            return true;
        }
        return false;
    }

    /** 获取玩家的累计岛屿删除次数（带缓存） */
    public int getDeleteCount(UUID playerUuid) {
        Integer cached = deleteCountCache.get(playerUuid);
        if (cached != null) {
            return cached;
        }
        int count = islandRepo.getDeleteCount(playerUuid);
        deleteCountCache.put(playerUuid, count);
        return count;
    }

    /** 增加玩家的岛屿删除次数（缓存 + 数据库原子递增） */
    public void incrementDeleteCount(UUID playerUuid) {
        deleteCountCache.merge(playerUuid, 1, Integer::sum);
        islandRepo.incrementDeleteCount(playerUuid);
    }

    /**
     * 添加成员到岛屿：如果该玩家当前是合作者则先移除合作者关系，
     * 再插入成员记录，整个操作在事务中完成。
     */
    public boolean addMemberToIsland(int islandId, UUID memberUuid, IslandPermissionLevel role) {
        Island island = islandsById.get(islandId);
        if (island == null) return false;

        boolean wasCoop = island.isCoop(memberUuid);

        if (wasCoop) {
            islandRepo.migrateCoopToMember(islandId, memberUuid, role.name());
        } else {
            islandRepo.addMember(islandId, memberUuid, role.name());
        }

        Player member = org.bukkit.Bukkit.getPlayer(memberUuid);
        if (member != null) {
            playerRepo.savePlayerName(memberUuid, member.getName());
        }

        if (wasCoop) {
            island.removeCoop(memberUuid);
            java.util.List<Integer> coopIslands = coopToIslandsIndex.get(memberUuid);
            if (coopIslands != null) {
                coopIslands.remove((Integer) islandId);
                if (coopIslands.isEmpty()) {
                    coopToIslandsIndex.remove(memberUuid);
                }
            }
        }

        island.addMember(memberUuid, role);
        memberToIslandIndex.put(memberUuid, islandId);
        return true;
    }

    /** 从岛屿移除成员，同步清理内存和数据库 */
    public boolean removeMemberFromIsland(int islandId, UUID memberUuid) {
        Island island = islandsById.get(islandId);
        if (island != null) {
            islandRepo.removeMember(islandId, memberUuid);
            island.removeMember(memberUuid);
            memberToIslandIndex.remove(memberUuid);
            playerRepo.setFirstNetherJoin(memberUuid, true);
            return true;
        }
        return false;
    }

    /** 添加合作者到岛屿（合作者拥有有限权限，不占用成员名额） */
    public boolean addCoopToIsland(int islandId, UUID playerUuid) {
        Island island = islandsById.get(islandId);
        if (island != null) {
            islandRepo.addCoop(islandId, playerUuid);

            Player player = org.bukkit.Bukkit.getPlayer(playerUuid);
            if (player != null) {
                playerRepo.savePlayerName(playerUuid, player.getName());
            }

            island.addCoop(playerUuid);
            coopToIslandsIndex.computeIfAbsent(playerUuid,
                    k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>())).add(islandId);
            return true;
        }
        return false;
    }

    /** 移除岛屿的合作者 */
    public boolean removeCoopFromIsland(int islandId, UUID playerUuid) {
        Island island = islandsById.get(islandId);
        if (island != null) {
            islandRepo.deleteCoop(islandId, playerUuid);

            island.removeCoop(playerUuid);
            java.util.List<Integer> coopIslands = coopToIslandsIndex.get(playerUuid);
            if (coopIslands != null) {
                coopIslands.remove((Integer) islandId);
                if (coopIslands.isEmpty()) {
                    coopToIslandsIndex.remove(playerUuid);
                }
            }
            return true;
        }
        return false;
    }

    /** 更新成员在岛屿中的权限组（晋升/降级） */
    public boolean updateMemberRole(int islandId, UUID memberUuid, IslandPermissionLevel newRole) {
        Island island = islandsById.get(islandId);
        if (island != null && island.getMembers().containsKey(memberUuid)) {
            islandRepo.updateMemberRole(islandId, memberUuid, newRole.name());
            island.setMemberRole(memberUuid, newRole);
            return true;
        }
        return false;
    }

    /** 更新岛屿刷石机等级，同步写库 */
    public boolean updateIslandGeneratorLevel(int islandId, int generatorLevel) {
        Island island = islandsById.get(islandId);
        if (island != null) {
            island.setGeneratorLevel(generatorLevel);
            islandRepo.updateGeneratorLevel(islandId, generatorLevel);
            return true;
        }
        return false;
    }

    /** 更新岛屿刷石机禁用矿石配置，同步写库 */
    public boolean updateIslandGeneratorDisabled(int id, String json) {
        Island island = islandsById.get(id);
        if (island != null) {
            islandRepo.updateGeneratorDisabled(id, json);
            return true;
        }
        return false;
    }

    /** 设置某项权限的最低等级要求 */
    public boolean setPermissionMinLevel(int islandId, IslandPermission permission, int minLevel) {
        Island island = islandsById.get(islandId);
        if (island != null) {
            island.setPermissionMinLevel(permission, minLevel);
            islandRepo.savePermissions(islandId, island.getPermissionsJson());
            return true;
        }
        return false;
    }

    /** 移除某项权限的自定义最低等级（恢复为 ALL 兜底值） */
    public boolean removePermissionMinLevel(int islandId, IslandPermission permission) {
        Island island = islandsById.get(islandId);
        if (island != null) {
            island.removePermissionMinLevel(permission);
            islandRepo.savePermissions(islandId, island.getPermissionsJson());
            return true;
        }
        return false;
    }

    /** 获取某项权限的最低等级要求 */
    public Integer getPermissionMinLevel(int islandId, IslandPermission permission) {
        Island island = islandsById.get(islandId);
        if (island != null) {
            return island.getPermissionMinLevel(permission);
        }
        return null;
    }

    /** 获取某玩家在指定岛屿中的权限组等级（默认 VISITOR） */
    public IslandPermissionLevel getPlayerRoleOnIsland(UUID playerUuid, int islandId) {
        Island island = islandsById.get(islandId);
        if (island != null) {
            return island.getMemberRole(playerUuid);
        }
        return IslandPermissionLevel.VISITOR;
    }

    // ==================== 岛屿区块查询 ====================

    /** 将岛屿加入网格空间索引（用于 O(1) 区块 → 岛屿反向查询） */
    private void addToGridIndex(Island island) {
        int gx = island.getCenterChunkX() / gridManager.getGridCellSize();
        int gz = island.getCenterChunkZ() / gridManager.getGridCellSize();
        long key = (((long) gx) << 32) | (gz & 0xffffffffL);
        islandGridIndex.put(key, island.getId());
    }

    /** 从网格空间索引中移除岛屿 */
    private void removeFromGridIndex(Island island) {
        int gx = island.getCenterChunkX() / gridManager.getGridCellSize();
        int gz = island.getCenterChunkZ() / gridManager.getGridCellSize();
        long key = (((long) gx) << 32) | (gz & 0xffffffffL);
        islandGridIndex.remove(key);
    }

    /**
     * 根据区块坐标 O(1) 查找所属岛屿（在当前半径内）。
     * 使用除法和四舍五入将区块坐标映射到网格单元，再通过 gridIndex 定位。
     */
    public Optional<Island> getIslandAt(int chunkX, int chunkZ) {
        int cellSize = gridManager.getGridCellSize();
        int gx = (int) Math.round((double) chunkX / cellSize);
        int gz = (int) Math.round((double) chunkZ / cellSize);
        long key = (((long) gx) << 32) | (gz & 0xffffffffL);
        Integer id = islandGridIndex.get(key);
        if (id != null) {
            Island island = islandsById.get(id);
            if (island != null && island.isChunkWithinIsland(chunkX, chunkZ)) {
                return Optional.of(island);
            }
        }
        return Optional.empty();
    }

    /**
     * 根据区块坐标查找所属岛屿（在最大可扩展半径内）。
     * 用于边界显示和传送门检测等需要"预判"扩展区域的场景。
     */
    public Optional<Island> getIslandAtMaxRange(int chunkX, int chunkZ) {
        int cellSize = gridManager.getGridCellSize();
        int gx = (int) Math.round((double) chunkX / cellSize);
        int gz = (int) Math.round((double) chunkZ / cellSize);
        long key = (((long) gx) << 32) | (gz & 0xffffffffL);
        Integer id = islandGridIndex.get(key);
        if (id != null) {
            Island island = islandsById.get(id);
            if (island != null && island.isChunkWithinMaxRange(chunkX, chunkZ)) {
                return Optional.of(island);
            }
        }
        return Optional.empty();
    }

    /** 从内存索引中移除岛屿（保留数据库记录，用于重载等场景） */
    public void removeIslandFromMemory(Island island) {
        islandsById.remove(island.getId());
        islandsByOwner.remove(island.getOwnerId());
        removeFromGridIndex(island);
        for (UUID memberUuid : island.getMembers().keySet()) {
            memberToIslandIndex.remove(memberUuid);
        }
        memberToIslandIndex.remove(island.getOwnerId());
        for (UUID coopUuid : island.getCoops()) {
            java.util.List<Integer> coopIslands = coopToIslandsIndex.get(coopUuid);
            if (coopIslands != null) {
                coopIslands.remove((Integer) island.getId());
                if (coopIslands.isEmpty()) {
                    coopToIslandsIndex.remove(coopUuid);
                }
            }
        }
    }

    /** 从数据库删除岛屿（含成员和合作者级联删除），保留内存索引 */
    public boolean deleteIslandFromDatabase(Island island) {
        islandRepo.deleteIslandCascade(island.getId());
        playerRepo.setFirstNetherJoin(island.getOwnerId(), true);
        return true;
    }

    /** 完整删除岛屿：数据库 + 所有内存索引全部清理 */
    public boolean deleteIsland(Island island) {
        islandRepo.deleteIslandCascade(island.getId());

        islandsById.remove(island.getId());
        islandsByOwner.remove(island.getOwnerId());
        removeFromGridIndex(island);

        return true;
    }
}
