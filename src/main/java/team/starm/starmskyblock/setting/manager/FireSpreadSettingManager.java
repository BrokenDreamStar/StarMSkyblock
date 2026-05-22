package team.starm.starmskyblock.setting.manager;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockSpreadEvent;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.setting.BaseSettingManager;
import team.starm.starmskyblock.setting.IslandSetting;

/**
 * 火势蔓延设置管理器
 *
 * 监听方块蔓延和方块燃烧事件，控制岛屿上的火势蔓延行为。
 * 当岛屿禁用了 FIRE_SPREAD 选项时，火不会向相邻方块扩散，
 * 已有的火焰也不会导致方块被烧毁。
 *
 * 注意：此管理器只拦截火/灵魂火的蔓延和燃烧，
 * 其他方块的蔓延（如藤蔓、蘑菇等）不受影响
 */
public class FireSpreadSettingManager extends BaseSettingManager {

    public FireSpreadSettingManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 处理方块蔓延事件，拦截火焰向相邻方块的扩散
     *
     * 只对火（FIRE）和灵魂火（SOUL_FIRE）的蔓延进行检查，
     * 其他方块的蔓延（如植物生长、液体流动等）直接放行
     *
     * 检查火焰蔓延的目标位置所在岛屿的 FIRE_SPREAD 设置，
     * 若禁用则取消事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        // 只处理火焰类方块的蔓延，其他方块（如藤蔓、蘑菇等）不拦截
        if (event.getSource().getType() != Material.FIRE
                && event.getSource().getType() != Material.SOUL_FIRE) {
            return;
        }

        // 检查火焰蔓延的目标位置所在岛屿是否允许火势蔓延
        if (!checkSetting(event.getBlock().getLocation(), IslandSetting.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    /**
     * 处理方块燃烧事件，拦截方块被火焰烧毁
     *
     * 当方块接触到火焰并即将被烧毁时触发此事件，
     * 检查该方块所在岛屿的 FIRE_SPREAD 设置，
     * 若禁用则取消事件，保护方块不被火焰烧毁
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!checkSetting(event.getBlock().getLocation(), IslandSetting.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }
}
