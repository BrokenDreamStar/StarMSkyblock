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

public class BlockPlaceListener implements Listener {

    private final SkyblockWorldManager worldManager;
    private final ConfigManager configManager;

    public BlockPlaceListener(SkyblockWorldManager worldManager, ConfigManager configManager) {
        this.worldManager = worldManager;
        this.configManager = configManager;
    }

    @EventHandler
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
