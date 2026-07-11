package team.starm.starmskyblock.task;

import team.starm.starmskyblock.task.reward.TaskReward;

import java.util.List;

/**
 * 单个任务定义
 * <p>
 * 一次扫描 YAML 的不可变产物：包含任务身份、类型、解锁前置、需求组（{@link RequirementGroup}）
 * 与奖励（{@link TaskReward}）。需求组采用"组内 OR、组间 AND"语义，详见 CLAUDE.md 任务系统小节。
 * </p>
 */
public class TaskDefinition {

    /** 任务 ID，格式为 {@code <目录>/<文件名>}，如 {@code Chapter1/task1_1} */
    private final String id;
    /** 所属章节 ID（目录名） */
    private final String categoryId;
    /** 章节内序号，用于 /is task submit/claim 定位 */
    private final int missionNumber;
    /** 任务显示名 */
    private final String name;
    /** 任务描述（用于 UI 展示） */
    private final String description;
    /** 任务类型，决定由哪个监听器追踪 */
    private final TaskType taskType;
    /** 同章节内前置任务 ID 列表，全部 claim 后本任务才解锁记录进度 */
    private final List<String> requiredMissions;
    /** 仅记自然产生进度（非玩家放置），仅对 BLOCK_BREAK/BLOCK_PLACE 有意义 */
    private final boolean onlyNatural;
    /** 需求组列表，组间 AND、组内 OR */
    private final List<RequirementGroup> requirements;
    /** 任务奖励 */
    private final TaskReward rewards;

    public TaskDefinition(String id, String categoryId, int missionNumber, String name, String description,
                          TaskType taskType, List<String> requiredMissions,
                          boolean onlyNatural, List<RequirementGroup> requirements,
                          TaskReward rewards) {
        this.id = id;
        this.categoryId = categoryId;
        this.missionNumber = missionNumber;
        this.name = name;
        this.description = description;
        this.taskType = taskType;
        this.requiredMissions = requiredMissions;
        this.onlyNatural = onlyNatural;
        this.requirements = requirements;
        this.rewards = rewards;
    }

    public String getId() { return id; }
    public String getCategoryId() { return categoryId; }
    public int getMissionNumber() { return missionNumber; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public TaskType getTaskType() { return taskType; }
    public List<String> getRequiredMissionIds() { return requiredMissions; }
    public boolean isOnlyNatural() { return onlyNatural; }
    public List<RequirementGroup> getRequirements() { return requirements; }
    public TaskReward getRewards() { return rewards; }

    /**
     * 需求组
     * <p>
     * 一组候选材料/实体类型 + 目标数量。{@link #getTypes()} 之间是"OR"关系（任一满足即可累加），
     * 不同 RequirementGroup 之间是"AND"关系（每组都需达成）。可选 {@link #getPotionType()}
     * 仅对 ITEM 任务生效，用于精确匹配药水基类型。
     * </p>
     */
    public static class RequirementGroup {
        /** 候选类型列表（材料名/实体类型名），组内 OR */
        private final List<String> types;
        /** 目标数量 */
        private final int amount;
        /** 药水基类型限定（可空），仅 ITEM 任务用于精确匹配药水 */
        private final String potionType;

        public RequirementGroup(List<String> types, int amount) {
            this(types, amount, null);
        }

        public RequirementGroup(List<String> types, int amount, String potionType) {
            this.types = types;
            this.amount = amount;
            this.potionType = potionType;
        }

        public List<String> getTypes() { return types; }
        public int getAmount() { return amount; }
        public String getPotionType() { return potionType; }
    }

}
