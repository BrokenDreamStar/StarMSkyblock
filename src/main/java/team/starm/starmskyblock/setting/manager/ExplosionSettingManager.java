package team.starm.starmskyblock.setting.manager;

import org.bukkit.entity.Creeper;
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
import team.starm.starmskyblock.setting.BaseSettingManager;
import team.starm.starmskyblock.setting.IslandSetting;

/**
 * 爆炸设置管理器
 *
 * 监听实体爆炸事件，根据爆炸源的类型分别检查岛屿上对应的设置项，
 * 精细控制不同类型爆炸对岛屿方块的破坏能力。
 *
 * 支持的爆炸源及对应设置项：
 * - Creeper（苦力怕）→ CREEPER_EXPLOSION
 * - TNT / ExplosiveMinecart → TNT_EXPLOSION
 * - Ghast Fireball（恶魂火球）→ GHAST_FIREBALL_GRIEF
 * - Wither / WitherSkull（凋灵及其头颅）→ WITHER_GRIEF
 */
public class ExplosionSettingManager extends BaseSettingManager {

    public ExplosionSettingManager(IslandManager islandManager, ConfigManager configManager,
                                     PublicAreaConfigManager publicAreaConfig,
                                     LockedAreaConfigManager lockedAreaConfig) {
        super(islandManager, configManager, publicAreaConfig, lockedAreaConfig);
    }

    /**
     * 处理实体爆炸事件，根据爆炸源类型取消爆炸
     *
     * 逐一判断爆炸源实体的类型：
     * 1. Creeper → 检查 CREEPER_EXPLOSION
     * 2. TNT / ExplosiveMinecart → 检查 TNT_EXPLOSION
     * 3. Ghast发射的Fireball → 检查 GHAST_FIREBALL_GRIEF
     * 4. Wither → 检查 WITHER_GRIEF
     * 5. WitherSkull → 检查 WITHER_GRIEF
     *
     * 若对应设置禁用，直接取消整个爆炸事件，
     * 该岛屿上的所有方块和实体都不会受到爆炸影响
     *
     * 使用 HIGH 优先级，忽略已取消的事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity == null) {
            return;
        }

        boolean cancelled = switch (entity) {
            case Creeper c -> !checkSetting(event.getLocation(), IslandSetting.CREEPER_EXPLOSION);
            case TNTPrimed t -> !checkSetting(event.getLocation(), IslandSetting.TNT_EXPLOSION);
            case ExplosiveMinecart e -> !checkSetting(event.getLocation(), IslandSetting.TNT_EXPLOSION);
            case Fireball f when f.getShooter() instanceof Ghast -> !checkSetting(event.getLocation(), IslandSetting.GHAST_FIREBALL_GRIEF);
            case Wither w -> !checkSetting(event.getLocation(), IslandSetting.WITHER_GRIEF);
            case WitherSkull w -> !checkSetting(event.getLocation(), IslandSetting.WITHER_GRIEF);
            default -> false;
        };
        if (cancelled) {
            event.setCancelled(true);
        }
    }
}
