package team.starm.starmskyblock.listener;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import team.starm.starmskyblock.StarMSkyblock;

public class EntityPortalListener implements Listener {

    private final StarMSkyblock plugin;

    public EntityPortalListener(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        Entity entity = event.getEntity();
        Location from = event.getFrom();
        World fromWorld = from.getWorld();

        if (fromWorld == null)
            return;

        String normalName = plugin.getConfigManager().getWorldNameNormal();
        String netherName = plugin.getConfigManager().getWorldNameNether();

        // 只处理下界传送门事件
        if (!fromWorld.getName().equals(normalName) && !fromWorld.getName().equals(netherName)) {
            return;
        }

        // 如果实体是玩家，使用玩家传送门逻辑
        if (entity instanceof Player) {
            return; // 玩家传送门由PlayerPortalEvent处理
        }

    }

}