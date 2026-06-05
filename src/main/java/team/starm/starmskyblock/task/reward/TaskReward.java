package team.starm.starmskyblock.task.reward;

import java.util.List;

public record TaskReward(List<String> commands) {

    public boolean isEmpty() {
        return commands == null || commands.isEmpty();
    }
}
