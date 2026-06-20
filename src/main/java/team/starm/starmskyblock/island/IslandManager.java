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

import com.google.gson.reflect.TypeToken;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
    /** 合作者反向索引：玩家 UUID → 可访问的岛屿 ID 集合 */
    private final Map<UUID, Set<Integer>> coopToIslandsIndex = new ConcurrentHashMap<>();
    /** 删除次数缓存（玩家 UUID → 已删除次数），避免频繁查库 */
    private final Map<UUID, Integer> deleteCountCache = new ConcurrentHashMap<>();
    /** 岛屿名称索引（名称小写 → 岛屿 ID 集合），实现 O(1) 名称查询 */
    private final Map<String, Set<Integer>> islandNameIndex = new ConcurrentHashMap<>();
    /** 下一个可用岛屿 ID（自增） */
    private final AtomicInteger nextIslandId = new AtomicInteger(0);
    /** createIsland 的 per-owner 锁，防止同一玩家并发 /is create 通过 containsKey 检查后双双进入创建流程 */
    private final Map<UUID, Object> createIslandLocks = new ConcurrentHashMap<>();

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
        // 收集启动期需要设置默认权限的岛屿,循环结束后一次 batch flush,
        // 避免在每个需要补权限的岛屿上独立 writeLock → prepare → executeUpdate。
        java.util.Map<Integer, String> defaultPermissionUpdates = new java.util.HashMap<>();

        for (IslandRepository.IslandRow row : islandRepo.loadAllIslands()) {
            int id = row.id();
            Island island = new Island(id, row.ownerUuid(), row.radius());
            island.setName(row.name());
            island.setCenterChunkX(row.centerX());
            island.setCenterChunkZ(row.centerZ());
            island.setMaxRadius(configManager.getIslandMaxRadius());
            island.setLevel(row.level());
            island.setExperience(row.totalExperience());
            island.setBaselineExperience(row.baselineExperience());
            island.setAuraSkillsContribution(row.auraskillsContribution());
            try {
                java.util.Map<String, Object> baselineMap = GSON.fromJson(row.baselineBlockCounts(), new TypeToken<Map<String, Object>>(){}.getType());
                if (baselineMap != null && !baselineMap.isEmpty()) {
                    java.util.Map<String, Long> parsed = new java.util.HashMap<>();
                    for (java.util.Map.Entry<String, Object> entry : baselineMap.entrySet()) {
                        if (entry.getValue() instanceof Number num) {
                            parsed.put(entry.getKey(), num.longValue());
                        }
                    }
                    island.setBaselineBlockCounts(parsed);
                }
            } catch (Exception e) {
                MessageUtil.consoleWarn("解析岛屿模板基线 JSON 失败，ID: " + id);
            }
            try {
                java.util.Map<String, Object> blockCountsMap = GSON.fromJson(row.blockCounts(), new TypeToken<Map<String, Object>>(){}.getType());
                if (blockCountsMap != null && !blockCountsMap.isEmpty()) {
                    java.util.Map<String, Long> parsed = new java.util.HashMap<>();
                    for (java.util.Map.Entry<String, Object> entry : blockCountsMap.entrySet()) {
                        if (entry.getValue() instanceof Number num) {
                            parsed.put(entry.getKey(), num.longValue());
                        }
                    }
                    island.setBlockCountsFromRaw(parsed);
                }
            } catch (Exception e) {
                MessageUtil.consoleWarn("解析岛屿方块计数 JSON 失败，ID: " + id);
            }
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
                defaultPermissionUpdates.put(id, island.getPermissionsJson());
            }

            String homeData = row.homeData();
            island.setHomeFromJson(homeData);
            if (homeData != null && !homeData.isEmpty() && !homeData.equals("{}")) {
                try {
                    HomeLocation home = HomeLocation.fromJson(homeData);
                    if (home != null) {
                        island.setCustomHome(home.getWorldType(), home.getX(), home.getY(), home.getZ(),
                                home.getYaw(), home.getPitch());
                    }
                } catch (Exception e) {
                    MessageUtil.consoleWarn("解析岛屿 home 数据失败，ID: " + id + "，数据: " + homeData);
                }
            }

            islandsById.put(id, island);
            islandsByOwner.put(row.ownerUuid(), island);
            memberToIslandIndex.put(row.ownerUuid(), id);
            addToGridIndex(island);
            addToNameIndex(island);

            if (id > maxId) {
                maxId = id;
            }
        }

        nextIslandId.set(maxId + 1);

        if (!defaultPermissionUpdates.isEmpty()) {
            islandRepo.batchSavePermissions(defaultPermissionUpdates);
        }

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
                        k -> ConcurrentHashMap.newKeySet()).add(row.islandId());
            }
        }

        MessageUtil.consolePrint("成功从数据库加载了 " + islandsById.size() + " 个岛屿。");
    }

    public Island createIsland(UUID ownerId, String schematicId, String name) {
        // 用 per-owner 锁串行化同一玩家的创建请求，防止 containsKey 与 put 之间
        // 另一线程通过检查 → 双重插入岛屿。
        Object lock = createIslandLocks.computeIfAbsent(ownerId, k -> new Object());
        synchronized (lock) {
            if (islandsByOwner.containsKey(ownerId)) {
                throw new IllegalStateException("该玩家已经拥有一个岛屿！");
            }

        int islandId = nextIslandId.getAndIncrement();
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
        addToNameIndex(island);

        String playerName = (owner != null) ? owner.getName() : ownerId.toString();
        MessageUtil.consolePrint("玩家" + playerName + "已创建岛屿 ID:" + islandId + " 中心区块坐标: " + location);

        return island;
        } // synchronized (lock)
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
        Set<Integer> ids = coopToIslandsIndex.get(playerUuid);
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
        Set<Integer> ids = islandNameIndex.get(islandName.toLowerCase());
        if (ids == null || ids.isEmpty()) return Optional.empty();
        return Optional.ofNullable(islandsById.get(ids.iterator().next()));
    }

    /** 根据岛屿名称查询所有匹配的岛屿（可能存在同名岛屿） */
    public java.util.List<Island> getIslandsByName(String islandName) {
        Set<Integer> ids = islandNameIndex.get(islandName.toLowerCase());
        if (ids == null || ids.isEmpty()) return java.util.Collections.emptyList();
        java.util.List<Island> result = new java.util.ArrayList<>(ids.size());
        for (Integer id : ids) {
            Island island = islandsById.get(id);
            if (island != null) {
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
                player.getLocation().getBlockX() >> 4,
                player.getLocation().getBlockZ() >> 4);
    }

    /** 判断某玩家当前是否在指定岛屿范围内 */
    public boolean isPlayerOnIsland(Player player, Island island) {
        return island.isChunkWithinIsland(
                player.getLocation().getBlockX() >> 4,
                player.getLocation().getBlockZ() >> 4);
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
            String oldName = island.getName();
            islandRepo.updateName(id, newName);
            island.setName(newName);
            if (oldName != null && !oldName.isEmpty()) {
                removeFromNameIndex(oldName, id);
            }
            if (newName != null && !newName.isEmpty()) {
                addToNameIndex(island);
            }
            return true;
        }
        return false;
    }

    /** 更新岛屿自定义传送点（含朝向），同步写库 */
    public boolean updateIslandCustomHome(int id, Island.WorldType worldType, double x, double y, double z, float yaw, float pitch) {
        Island island = islandsById.get(id);
        if (island != null) {
            String json = HomeLocation.toJson(worldType, x, y, z, yaw, pitch);
            islandRepo.updateHomeData(id, json);
            island.setCustomHome(worldType, x, y, z, yaw, pitch);
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
    public boolean updateIslandSettings(int islandId) {
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
        islandRepo.incrementDeleteCount(playerUuid);
        deleteCountCache.merge(playerUuid, 1, Integer::sum);
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
            Set<Integer> coopIslands = coopToIslandsIndex.get(memberUuid);
            if (coopIslands != null) {
                coopIslands.remove(islandId);
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
                    k -> ConcurrentHashMap.newKeySet()).add(islandId);
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
            Set<Integer> coopIslands = coopToIslandsIndex.get(playerUuid);
            if (coopIslands != null) {
                coopIslands.remove(islandId);
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
        int cellSize = gridManager.getGridCellSize();
        int gx = (int) Math.round((double) island.getCenterChunkX() / cellSize);
        int gz = (int) Math.round((double) island.getCenterChunkZ() / cellSize);
        long key = (((long) gx) << 32) | (gz & 0xffffffffL);
        islandGridIndex.put(key, island.getId());
    }

    /** 从网格空间索引中移除岛屿 */
    private void removeFromGridIndex(Island island) {
        int cellSize = gridManager.getGridCellSize();
        int gx = (int) Math.round((double) island.getCenterChunkX() / cellSize);
        int gz = (int) Math.round((double) island.getCenterChunkZ() / cellSize);
        long key = (((long) gx) << 32) | (gz & 0xffffffffL);
        islandGridIndex.remove(key);
    }

    /** 将岛屿加入名称索引 */
    private void addToNameIndex(Island island) {
        String name = island.getName();
        if (name != null && !name.isEmpty()) {
            islandNameIndex.computeIfAbsent(name.toLowerCase(),
                    k -> ConcurrentHashMap.newKeySet()).add(island.getId());
        }
    }

    /** 从名称索引中移除指定岛屿 */
    private void removeFromNameIndex(Island island) {
        String name = island.getName();
        if (name != null && !name.isEmpty()) {
            Set<Integer> ids = islandNameIndex.get(name.toLowerCase());
            if (ids != null) {
                ids.remove(island.getId());
                if (ids.isEmpty()) {
                    islandNameIndex.remove(name.toLowerCase());
                }
            }
        }
    }

    /** 从名称索引中移除指定名称的特定 ID */
    private void removeFromNameIndex(String name, int islandId) {
        if (name != null && !name.isEmpty()) {
            Set<Integer> ids = islandNameIndex.get(name.toLowerCase());
            if (ids != null) {
                ids.remove(islandId);
                if (ids.isEmpty()) {
                    islandNameIndex.remove(name.toLowerCase());
                }
            }
        }
    }

    /**
     * 根据区块坐标 O(1) 查找所属岛屿（在当前半径内）。
     * 使用 Math.round 将区块坐标映射到最近的网格中心，保证岛屿负方向区块也能正确归位。
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
        cleanupIndices(island);
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
        cleanupIndices(island);
        return true;
    }

    /**
     * 从 6 个内存索引中清理岛屿。
     * 由 {@link #removeIslandFromMemory(Island)} 和 {@link #deleteIsland(Island)} 共用，
     * 避免两处维护同一份索引清理逻辑产生漂移。
     */
    private void cleanupIndices(Island island) {
        islandsById.remove(island.getId());
        islandsByOwner.remove(island.getOwnerId());
        removeFromGridIndex(island);

        for (UUID memberUuid : island.getMembers().keySet()) {
            memberToIslandIndex.remove(memberUuid);
        }
        memberToIslandIndex.remove(island.getOwnerId());

        for (UUID coopUuid : island.getCoops()) {
            Set<Integer> coopIslands = coopToIslandsIndex.get(coopUuid);
            if (coopIslands != null) {
                coopIslands.remove(island.getId());
                if (coopIslands.isEmpty()) {
                    coopToIslandsIndex.remove(coopUuid);
                }
            }
        }
        deleteCountCache.remove(island.getOwnerId());
        removeFromNameIndex(island);
    }

    /**
     * 获取岛屿数据访问层
     */
    public IslandRepository getIslandRepository() {
        return islandRepo;
    }
}
