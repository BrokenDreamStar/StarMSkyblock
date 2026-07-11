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

/**
 * FARMING 任务监听器
 * <p>
 * 玩家破坏作物时计入进度。仅当方块属于 {@link #CROPS} 且为成熟状态
 * （{@link Ageable#getAge()} 达到最大值）时才记录，避免未成熟收获被统计。
 * 部分作物材料名与任务键不一致（如 CARROTS 材料对应 CARROT 键），在此做归一映射。
 * </p>
 */
public class FarmingTaskListener extends BaseTaskListener {

    /** 可被统计的作物材料集合（含瓜类、甘蔗等非年龄型作物） */
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

    /** 仅记成熟作物破坏；部分材料名归一为任务配置使用的键 */
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

        // 材料名(复数)与任务键(单数)不一致的在此归一，其余沿用材料名
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
