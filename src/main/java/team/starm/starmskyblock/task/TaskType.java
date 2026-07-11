package team.starm.starmskyblock.task;

/**
 * 任务类型枚举
 * <p>
 * 定义任务系统中所有可追踪的进度类型。每种类型对应 {@code task/listener/} 下一个
 * {@code BaseTaskListener} 子类，由 {@link TaskManager} 在启动时按配置中实际出现的类型
 * 选择性注册监听器。
 * </p>
 * <ul>
 *   <li>{@code BLOCK_BREAK} / {@code BLOCK_PLACE} -- 方块破坏/放置，按材料名匹配，受 only-natural 约束</li>
 *   <li>{@code ITEM} -- 手动提交型，玩家用 {@code /is task submit} 交付物品，支持 potion_type 匹配</li>
 *   <li>{@code ENTITY_KILL} -- 击杀实体，按实体类型名匹配</li>
 *   <li>{@code FARMING} -- 作物收获，按作物材料名匹配（仅记成熟作物）</li>
 *   <li>{@code FISHING} -- 钓鱼，按钓获物材料名匹配</li>
 *   <li>{@code CRAFTING} -- 合成，按结果物品材料名匹配</li>
 *   <li>{@code EARN_MONEY} -- Vault 余额增长，增量计入 {@code money} 键</li>
 * </ul>
 */
public enum TaskType {
    BLOCK_BREAK,
    BLOCK_PLACE,
    ITEM,
    ENTITY_KILL,
    FARMING,
    FISHING,
    CRAFTING,
    EARN_MONEY
}
