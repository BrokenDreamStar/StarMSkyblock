package team.starm.starmskyblock.setting.manager;

import org.bukkit.entity.Creeper;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityExplodeEvent;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.world.SkyblockWorldManager;
import team.starm.starmskyblock.setting.BaseSettingManager;
import team.starm.starmskyblock.setting.IslandSetting;

/**
 * 爆炸设置管理器
 *
 * 监听实体爆炸事件，按爆炸源类型映射到对应设置项，过滤受影响方块，
 * 精细控制不同类型爆炸对岛屿方块的破坏能力。
 *
 * 支持的爆炸源及对应设置项：
 * - Creeper（苦力怕）-> CREEPER_EXPLOSION
 * - TNT / ExplosiveMinecart -> TNT_EXPLOSION
 * - Ghast Fireball（恶魂火球）-> GHAST_FIREBALL_GRIEF
 * - Wither / WitherSkull（凋灵及其头颅）-> WITHER_GRIEF
 *
 * 爆炸源所在位置禁用该类爆炸 -> 取消整个事件（保留原保守行为）。
 * 爆炸源允许时 -> 逐方块按其自身位置的设置过滤，移除禁爆区域的方块，
 * 避免在公共区/边界外引爆时摧毁相邻禁爆岛屿的方块（部分爆炸，保护区方块保留）。
 */
public class ExplosionSettingManager extends BaseSettingManager {

    public ExplosionSettingManager(IslandManager islandManager, ConfigManager configManager,
                                     PublicAreaConfigManager publicAreaConfig,
                                     LockedAreaConfigManager lockedAreaConfig,
                                     JavaPlugin plugin, SkyblockWorldManager worldManager) {
        super(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
    }

    /**
     * 处理实体爆炸事件，按爆炸源类型过滤受影响方块。
     *
     * 使用 HIGH 优先级，忽略已取消的事件。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity == null) {
            return;
        }

        // 按爆炸源类型确定对应的设置项；不支持的来源不做任何拦截
        IslandSetting setting = switch (entity) {
            case Creeper c -> IslandSetting.CREEPER_EXPLOSION;
            case TNTPrimed t -> IslandSetting.TNT_EXPLOSION;
            case ExplosiveMinecart e -> IslandSetting.TNT_EXPLOSION;
            case Fireball f when f.getShooter() instanceof Ghast -> IslandSetting.GHAST_FIREBALL_GRIEF;
            case Wither w -> IslandSetting.WITHER_GRIEF;
            case WitherSkull w -> IslandSetting.WITHER_GRIEF;
            default -> null;
        };
        if (setting == null) {
            return;
        }

        // 爆炸源所在位置禁用该类爆炸 -> 取消整个事件
        if (!checkSetting(event.getLocation(), setting)) {
            event.setCancelled(true);
            return;
        }

        // 爆炸源允许：按每个受影响方块自身位置的设置过滤，移除禁爆区域的方块。
        // checkSetting 内部用 >>4 取区块坐标并尊重 public/locked-area 覆盖，无同步区块加载。
        event.blockList().removeIf(block -> !checkSetting(block.getLocation(), setting));
    }
}
