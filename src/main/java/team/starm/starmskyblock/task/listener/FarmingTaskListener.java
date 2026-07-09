package team.starm.starmskyblock.task.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskType;

import java.util.Set;

public class FarmingTaskListener extends BaseTaskListener {

    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.NETHER_WART, Material.MELON,
            Material.PUMPKIN, Material.SUGAR_CANE, Material.CACTUS,
            Material.COCOA, Material.SWEET_BERRY_BUSH, Material.KELP,
            Material.BAMBOO
    );

    public FarmingTaskListener(TaskManager taskManager) {
        super(taskManager, TaskType.FARMING);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCropHarvest(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        Block block = event.getBlock();
        Material type = block.getType();

        if (!CROPS.contains(type)) return;

        if (block.getBlockData() instanceof Ageable age) {
            if (age.getAge() < age.getMaximumAge()) return;
        }

        String cropName = switch (type) {
            case WHEAT -> "WHEAT";
            case CARROTS -> "CARROT";
            case POTATOES -> "POTATO";
            case BEETROOTS -> "BEETROOT";
            default -> type.name();
        };

        track(player, cropName, 1);
    }
}
