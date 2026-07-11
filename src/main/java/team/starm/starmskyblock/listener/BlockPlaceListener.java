package team.starm.starmskyblock.listener;

import net.kyori.adventure.audience.Audience;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.world.SkyblockWorldManager;

/**
 * 末地传送门框架放置限制监听器
 * <p>
 * 监听玩家在下界/末地放置末地传送门框架的行为，根据配置项决定是否拦截。
 * 用于防止玩家在不期望的维度自行搭建末地传送门绕过正常的传送流程。
 * </p>
 */
public class BlockPlaceListener implements Listener {

    /** 世界管理器，用于判定当前方块所在维度 */
    private final SkyblockWorldManager worldManager;
    /** 配置管理器，读取各维度是否允许放置末地传送门 */
    private final ConfigManager configManager;

    public BlockPlaceListener(SkyblockWorldManager worldManager, ConfigManager configManager) {
        this.worldManager = worldManager;
        this.configManager = configManager;
    }

    /**
     * 监听方块放置事件
     * <p>
     * 仅关注末地传送门框架的放置。当玩家在下界放置且配置未允许、或在末地放置且配置未允许时，
     * 取消事件并向其发送 action bar 提示。
     * </p>
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.END_PORTAL_FRAME) return;

        World world = event.getBlock().getWorld();
        String worldName = world.getName();

        if (worldManager.isNetherWorld(worldName) && !configManager.isAllowEndPortalInNether()) {
            event.setCancelled(true);
            ((Audience) event.getPlayer()).sendActionBar(MessageUtil.parse("&c禁止在下界放置末地传送门"));
        } else if (worldManager.isEndWorld(worldName) && !configManager.isAllowEndPortalInEnd()) {
            event.setCancelled(true);
            ((Audience) event.getPlayer()).sendActionBar(MessageUtil.parse("&c禁止在末地放置末地传送门"));
        }
    }
}
