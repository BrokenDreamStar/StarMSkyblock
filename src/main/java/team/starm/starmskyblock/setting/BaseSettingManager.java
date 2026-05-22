package team.starm.starmskyblock.setting;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Listener;

import team.starm.starmskyblock.config.ConfigManager;
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

    public BaseSettingManager(IslandManager islandManager, ConfigManager configManager) {
        this.islandManager = islandManager;
        this.configManager = configManager;
    }

    /**
     * 检查指定位置的指定设置项是否启用
     *
     * 处理逻辑：
     * 1. 如果位置不在空岛世界（主世界/下界/末地）中，直接返回 true（放行）
     * 2. 如果位置不属于任何岛屿（无主区域），也返回 true（放行）
     * 3. 否则查询该岛屿对应设置项的布尔值
     *
     * @param location 要检查的位置
     * @param setting  要检查的设置选项
     * @return true=设置已启用/不适用，false=设置被禁用（调用方应取消事件）
     */
    public boolean checkSetting(Location location, IslandSetting setting) {
        // 只处理空岛世界的区块，非空岛世界（如主城、资源世界）一律放行
        String worldName = location.getWorld().getName();
        if (!worldName.equals(configManager.getWorldNameNormal())
                && !worldName.equals(configManager.getWorldNameNether())
                && !worldName.equals(configManager.getWorldNameEnd())) {
            return true;
        }

        // 如果该位置没有对应的岛屿（无主区/虚空等），同样放行
        Island island = getIslandAt(location);
        if (island == null) {
            return true;
        }
        // 委托给 Island 对象查询其设置值
        return island.getSetting(setting);
    }

    /**
     * 根据位置查找该位置所在的岛屿
     * 通过区块坐标到 IslandManager 中查找，只有空岛世界的坐标才会被匹配
     * 非空岛世界直接返回 null
     *
     * @param location 要查找的位置
     * @return 该位置所属的 Island 对象，找不到或不在空岛世界时返回 null
     */
    protected Island getIslandAt(Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        String worldName = world.getName();
        if (!worldName.equals(configManager.getWorldNameNormal())
                && !worldName.equals(configManager.getWorldNameNether())
                && !worldName.equals(configManager.getWorldNameEnd())) {
            return null;
        }

        return islandManager.getIslandAt(
                location.getChunk().getX(),
                location.getChunk().getZ()
        ).orElse(null);
    }
}
