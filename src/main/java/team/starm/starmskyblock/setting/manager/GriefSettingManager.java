package team.starm.starmskyblock.setting.manager;

import org.bukkit.entity.Enderman;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.world.SkyblockWorldManager;
import team.starm.starmskyblock.setting.BaseSettingManager;
import team.starm.starmskyblock.setting.IslandSetting;

/**
 * 实体破坏方块设置管理器
 *
 * 监听实体改变方块的事件，控制特定实体对岛屿方块的破坏行为。
 * 目前覆盖两种实体：
 * - 末影人（Enderman）：搬运/放置方块
 * - 凋灵（Wither）：破坏路径上的方块
 *
 * 每种实体分别对应独立的设置项，便于玩家精细控制
 */
public class GriefSettingManager extends BaseSettingManager {

    public GriefSettingManager(IslandManager islandManager, ConfigManager configManager,
                                PublicAreaConfigManager publicAreaConfig,
                                LockedAreaConfigManager lockedAreaConfig,
                                JavaPlugin plugin, SkyblockWorldManager worldManager) {
        super(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
    }

    /**
     * 处理实体改变方块的事件
     *
     * 根据实体类型检查对应设置：
     * - Enderman → 检查 ENDERMAN_GRIEF
     * - Wither  → 检查 WITHER_GRIEF
     *
     * 检查的是被改变方块位置所在岛屿的设置值
     * 若对应设置禁用则取消事件，保护方块不被破坏
     *
     * 使用 HIGH 优先级，忽略已取消的事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();

        boolean cancelled = switch (entity) {
            case Enderman e -> !checkSetting(event.getBlock().getLocation(), IslandSetting.ENDERMAN_GRIEF);
            case Wither w -> !checkSetting(event.getBlock().getLocation(), IslandSetting.WITHER_GRIEF);
            default -> false;
        };
        if (cancelled) {
            event.setCancelled(true);
        }
    }
}
