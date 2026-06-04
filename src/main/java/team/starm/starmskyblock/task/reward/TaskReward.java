package team.starm.starmskyblock.task.reward;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record TaskReward(List<ItemStack> items, List<String> commands) {

    public boolean isEmpty() {
        return (items == null || items.isEmpty()) && (commands == null || commands.isEmpty());
    }
}
