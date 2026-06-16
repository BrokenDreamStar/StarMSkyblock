package team.starm.starmskyblock.setting.manager;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Entity;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.setting.BaseSettingManager;
import team.starm.starmskyblock.setting.IslandSetting;

/**
 * PVP 设置管理器
 *
 * 监听实体伤害事件，当玩家在岛屿上攻击另一名玩家时，
 * 检查目标所在岛屿的 PVP 设置项是否启用。
 * 若 PVP 被禁用，则取消伤害事件并提示攻击者。
 *
 * 支持直接攻击（玩家打玩家）和间接攻击（如弓箭、投掷物）
 */
public class PvpSettingManager extends BaseSettingManager {

    public PvpSettingManager(IslandManager islandManager, ConfigManager configManager,
                              PublicAreaConfigManager publicAreaConfig,
                              LockedAreaConfigManager lockedAreaConfig) {
        super(islandManager, configManager, publicAreaConfig, lockedAreaConfig);
    }

    /**
     * 处理实体伤害事件，拦截玩家间的战斗行为
     *
     * 触发条件：
     * - 被伤害者必须是玩家
     * - 攻击者必须是玩家或玩家发射的弹射物
     * - 被伤害者所在岛屿禁用了 PVP 设置
     *
     * 使用 HIGH 优先级以确保在其他插件处理之后进行拦截，
     * 并忽略已取消的事件以避免重复处理
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // 只处理玩家被伤害的情况
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        // 解析实际攻击者（直接玩家或弹射物发射者）
        Player damager = getPlayerDamager(event.getDamager());
        if (damager == null) {
            return;
        }

        // 检查目标所在岛屿的 PVP 设置
        Location location = target.getLocation();
        if (!checkSetting(location, IslandSetting.PVP)) {
            event.setCancelled(true);
            MessageUtil.sendMessage(damager, "&e岛屿保护 &f|&c 该岛屿已禁用PVP！");
        }
    }

    /**
     * 从伤害源实体中提取玩家攻击者
     *
     * 处理两种场景：
     * 1. 直接攻击：damager 本身就是 Player 实例
     * 2. 远程攻击：damager 是弹射物（Projectile），需要获取其发射者（shooter）
     *
     * 非玩家造成的伤害（如怪物攻击、环境伤害、坠落等）直接返回 null
     *
     * @param damager 事件中的伤害源实体
     * @return 玩家攻击者，若非玩家攻击则返回 null
     */
    private Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        } else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }
}
