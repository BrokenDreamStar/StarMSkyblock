package team.starm.starmskyblock.setting;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Listener;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;

/**
 * 设置检查基类
 * 提供 checkSetting / getIslandAt 等子管理器共用的方法
 */
public abstract class BaseSettingManager implements Listener {

    protected final IslandManager islandManager;
    protected final ConfigManager configManager;

    public BaseSettingManager(IslandManager islandManager, ConfigManager configManager) {
        this.islandManager = islandManager;
        this.configManager = configManager;
    }

    public boolean checkSetting(Location location, IslandSetting setting) {
        String worldName = location.getWorld().getName();
        if (!worldName.equals(configManager.getWorldNameNormal())
                && !worldName.equals(configManager.getWorldNameNether())
                && !worldName.equals(configManager.getWorldNameEnd())) {
            return true;
        }

        Island island = getIslandAt(location);
        if (island == null) {
            return true;
        }
        return island.getSetting(setting);
    }

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
