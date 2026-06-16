package team.starm.starmskyblock.setting;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Listener;

import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;

/**
 * 岛屿设置检查的抽象基类
 *
 * 为所有具体的设置子管理器（PVP、生成、爆炸等）提供通用的能力：
 * - 判断某位置是否在任意一个岛屿范围内
 * - 检查该岛屿的某个设置选项是否启用
 * - 过滤非空岛世界的位置，非空岛世界不做任何拦截
 *
 * 子类通过实现 Listener 接口注册事件，在处理事件时调用本类提供的
 * checkSetting() 方法来判断是否需要取消事件
 */
public abstract class BaseSettingManager implements Listener {

    /** 岛屿管理器的引用，用于根据坐标查询岛屿 */
    protected final IslandManager islandManager;
    /** 配置管理器，用于获取世界名称等全局配置 */
    protected final ConfigManager configManager;
    /** 公共区域配置管理器 */
    protected final PublicAreaConfigManager publicAreaConfig;
    /** 未解锁区域配置管理器 */
    protected final LockedAreaConfigManager lockedAreaConfig;

    public BaseSettingManager(IslandManager islandManager, ConfigManager configManager,
                              PublicAreaConfigManager publicAreaConfig,
                              LockedAreaConfigManager lockedAreaConfig) {
        this.islandManager = islandManager;
        this.configManager = configManager;
        this.publicAreaConfig = publicAreaConfig;
        this.lockedAreaConfig = lockedAreaConfig;
    }

    /**
     * 检查指定位置的指定设置项是否启用
     *
     * 处理逻辑：
     * 1. 如果位置不在空岛世界中，直接返回 true（放行）
     * 2. 如果位置在岛屿当前已解锁区域内，检查该岛屿对应设置项
     * 3. 如果位置在岛屿未解锁区域（锁定区域），检查锁定区域配置
     * 4. 如果位置不属于任何岛屿（无主区/虚空等），检查公共区域配置
     *
     * @param location 要检查的位置
     * @param setting  要检查的设置选项
     * @return true=设置已启用/不适用，false=设置被禁用（调用方应取消事件）
     */
    public boolean checkSetting(Location location, IslandSetting setting) {
        String worldName = location.getWorld().getName();
        if (!StarMSkyblock.getInstance().getWorldManager().isSkyblockWorldName(worldName)) {
            if (StarMSkyblock.getInstance().getWorldManager().isPublicWorld(worldName)) {
                return !publicAreaConfig.isEnabled() || publicAreaConfig.getSetting(setting);
            }
            return true;
        }

        // 先检查最大范围，判断是否在锁定区域
        Island maxRangeIsland = getIslandAtMaxRange(location);
        if (maxRangeIsland != null) {
            int chunkX = location.getChunk().getX();
            int chunkZ = location.getChunk().getZ();
            if (maxRangeIsland.isChunkWithinIsland(chunkX, chunkZ)) {
                // 在已解锁区域内 → 使用岛屿自身的设置
                return maxRangeIsland.getSetting(setting);
            }
            // 在未解锁区域（锁定区域） → 使用锁定区域配置
            return !lockedAreaConfig.isEnabled() || lockedAreaConfig.getSetting(setting);
        }

        // 无主区域（公共区域） → 使用公共区域配置
        return !publicAreaConfig.isEnabled() || publicAreaConfig.getSetting(setting);
    }

    /**
     * 根据位置查找该位置所在的岛屿（最大范围内）
     * 用于检测未解锁区域（锁定区域）
     *
     * @param location 要查找的位置
     * @return 该位置所属的 Island 对象，找不到或不在空岛世界时返回 null
     */
    public Island getIslandAtMaxRange(Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        if (!StarMSkyblock.getInstance().getWorldManager().isSkyblockWorldName(world.getName())) {
            return null;
        }

        return islandManager.getIslandAtMaxRange(
                location.getChunk().getX(),
                location.getChunk().getZ()
        ).orElse(null);
    }

    /**
     * 根据位置查找该位置所在的岛屿（当前已解锁半径内）
     * 通过区块坐标到 IslandManager 中查找，只有空岛世界的坐标才会被匹配
     * 非空岛世界直接返回 null
     *
     * @param location 要查找的位置
     * @return 该位置所属的 Island 对象，找不到或不在空岛世界时返回 null
     */
    protected Island getIslandAt(Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        if (!StarMSkyblock.getInstance().getWorldManager().isSkyblockWorldName(world.getName())) {
            return null;
        }

        return islandManager.getIslandAt(
                location.getChunk().getX(),
                location.getChunk().getZ()
        ).orElse(null);
    }
}
