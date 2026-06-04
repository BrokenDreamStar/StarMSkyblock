package team.starm.starmskyblock.task.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskType;

public class CraftingTaskListener extends BaseTaskListener {

    public CraftingTaskListener(TaskManager taskManager) {
        super(taskManager, TaskType.CRAFTING);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.CRAFTING
                && event.getInventory().getType() != InventoryType.WORKBENCH) {
            return;
        }

        if (event.getSlot() != 0) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) return;

        int amount = result.getAmount();
        track(player, result.getType().name(), amount);
    }
}
