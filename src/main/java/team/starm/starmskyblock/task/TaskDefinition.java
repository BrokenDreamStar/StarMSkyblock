package team.starm.starmskyblock.task;

import team.starm.starmskyblock.task.reward.TaskReward;

import java.util.List;

public class TaskDefinition {

    private final String id;
    private final String categoryId;
    private final int missionNumber;
    private final String name;
    private final String description;
    private final TaskType taskType;
    private final List<String> requiredMissions;
    private final boolean onlyNatural;
    private final List<RequirementGroup> requirements;
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

    public static class RequirementGroup {
        private final List<String> types;
        private final int amount;
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
