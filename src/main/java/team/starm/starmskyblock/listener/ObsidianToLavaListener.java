package team.starm.starmskyblock.listener;

import java.util.HashMap;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.BasePermissionManager;
import team.starm.starmskyblock.permission.IslandPermission;

public class ObsidianToLavaListener extends BasePermissionManager {

    public ObsidianToLavaListener(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onObsidianClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack handItem = event.getItem();
        Block clickedBlock = event.getClickedBlock();

        if (handItem == null || clickedBlock == null) {
            return;
        }

        if (handItem.getType() != Material.BUCKET || clickedBlock.getType() != Material.OBSIDIAN) {
            return;
        }

        Location loc = clickedBlock.getLocation();

        if (!checkPermission(loc, player.getUniqueId(), IslandPermission.BREAK)) {
            event.setCancelled(true);
            event.setUseInteractedBlock(PlayerInteractEvent.Result.DENY);
            event.setUseItemInHand(PlayerInteractEvent.Result.DENY);
            sendDenyMessage(player, IslandPermission.BREAK);
            return;
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(PlayerInteractEvent.Result.DENY);
        event.setUseItemInHand(PlayerInteractEvent.Result.DENY);

        if (player.getGameMode() != GameMode.CREATIVE) {
            int amount = handItem.getAmount();
            if (amount > 1) {
                handItem.setAmount(amount - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }

        clickedBlock.setType(Material.AIR);

        ItemStack lavaBucket = new ItemStack(Material.LAVA_BUCKET);
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(lavaBucket);
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }
}
