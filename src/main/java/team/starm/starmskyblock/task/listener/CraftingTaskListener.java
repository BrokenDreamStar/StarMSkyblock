package team.starm.starmskyblock.task.listener;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.TaskType;

/**
 * CRAFTING 任务监听器
 * <p>
 * 监听合成台/工作台的结果取出事件（{@link CraftItemEvent}），按实际合成数量计入进度。
 * 仅 CRAFTING 与 WORKBENCH 两种背包类型会触发该事件。
 * </p>
 * <p>
 * shift-click 合成时 {@link CraftItemEvent} 只触发一次，且 {@code getCurrentItem().getAmount()}
 * 仅为单次配方产量（常为 1），直接使用会让一次 shift 合成 64 个仅计 1，几乎无法完成任务。
 * 本监听器按"矩阵原料上限"与"背包可容纳次数"取较小值算出实际合成次数，再乘以单次产量，
 * 得到真实合成数。
 * </p>
 */
public class CraftingTaskListener extends BaseTaskListener {

    public CraftingTaskListener(TaskManager taskManager) {
        super(taskManager, TaskType.CRAFTING);
    }

    /** 取出合成结果时按实际合成数量计入进度 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) return;

        int amount = computeCraftAmount(event, result);
        if (amount > 0) {
            track(player, result.getType().name(), amount);
        }
    }

    /**
     * 计算本次点击的实际合成数量。
     * <ul>
     *   <li>普通点击：单次配方产量 {@code result.getAmount()}。</li>
     *   <li>shift-click：min(矩阵原料可支持次数, 背包可容纳次数) × 单次产量。
     *       矩阵次数取所有非空原料格堆叠数的最小值（标准配方每次合成各消耗 1）；
     *       背包次数按结果物剩余容量折算。少数返回容器的配方（如空桶）按常规全消耗估算，
     *       与原实现一致地不做特殊处理。</li>
     * </ul>
     */
    private int computeCraftAmount(CraftItemEvent event, ItemStack result) {
        int perCraft = result.getAmount();
        if (!event.isShiftClick()) {
            return perCraft;
        }

        int maxByIngredients = maxCraftableFromMatrix(event.getInventory());
        if (maxByIngredients <= 0) {
            // 矩阵无有效原料（异常情况），退化为单次产量
            return perCraft;
        }

        int maxBySpace = operationsBySpace(event.getWhoClicked(), result, perCraft);
        int operations = Math.min(maxByIngredients, maxBySpace);
        return operations * perCraft;
    }

    /** 矩阵原料可支持的合成次数：取非空原料格堆叠数的最小值 */
    private int maxCraftableFromMatrix(org.bukkit.inventory.Inventory inventory) {
        if (!(inventory instanceof CraftingInventory crafting)) return 0;
        int max = Integer.MAX_VALUE;
        for (ItemStack matrixItem : crafting.getMatrix()) {
            if (matrixItem == null || matrixItem.getType().isAir()) continue;
            max = Math.min(max, matrixItem.getAmount());
        }
        return max == Integer.MAX_VALUE ? 0 : max;
    }

    /** 玩家背包可容纳的结果物数量折算为合成次数 */
    private int operationsBySpace(HumanEntity whoClicked, ItemStack result, int perCraft) {
        if (!(whoClicked instanceof Player player)) return 0;
        int maxStack = result.getMaxStackSize();
        int capacity = 0;
        for (ItemStack invItem : player.getInventory().getStorageContents()) {
            if (invItem == null || invItem.getType().isAir()) {
                capacity += maxStack;
            } else if (invItem.isSimilar(result)) {
                capacity += maxStack - invItem.getAmount();
            }
        }
        return capacity / perCraft;
    }
}
