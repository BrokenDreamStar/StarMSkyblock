package team.starm.starmskyblock.task.reward;

import java.util.List;

public record TaskReward(List<String> commands, List<ItemReward> items) {

    public record ItemReward(String material, int amount) {}

    public boolean isEmpty() {
        return (commands == null || commands.isEmpty())
            && (items == null || items.isEmpty());
    }
}
