package team.starm.starmskyblock.task.reward;

import java.util.List;

/**
 * 任务奖励定义
 * <p>
 * 不可变记录，承载任务 claim 时发放的三类奖励：金币、命令、物品。
 * 由 {@code TaskConfigScanner} 从任务 YAML 的 {@code reward} 段解析，
 * 在 {@link team.starm.starmskyblock.task.TaskManager} 发奖时消费。
 * </p>
 */
public record TaskReward(double money, List<String> commands, List<ItemReward> items) {

    /** 单件物品奖励：材料名（Minecraft material key）与数量 */
    public record ItemReward(String material, int amount) {}

    /** 是否无任何奖励（金币<=0 且无命令且无物品），用于跳过发奖逻辑 */
    public boolean isEmpty() {
        return money <= 0
            && (commands == null || commands.isEmpty())
            && (items == null || items.isEmpty());
    }
}
