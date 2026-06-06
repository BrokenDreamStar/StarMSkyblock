package team.starm.starmskyblock.task.reward;

import java.util.List;

public record TaskReward(double money, List<String> commands, List<ItemReward> items) {

    public record ItemReward(String material, int amount) {}

    public boolean isEmpty() {
        return money <= 0
            && (commands == null || commands.isEmpty())
            && (items == null || items.isEmpty());
    }
}
