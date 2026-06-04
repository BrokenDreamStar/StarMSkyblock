package team.starm.starmskyblock.task;

import team.starm.starmskyblock.task.reward.TaskReward;

import java.util.List;
import java.util.Map;

public class TaskDefinition {

    private final String id;
    private final String categoryId;
    private final String name;
    private final TaskType taskType;
    private final boolean autoReward;
    private final boolean resetAfterFinish;
    private final List<String> requiredMissionIds;
    private final List<RequirementGroup> requirements;
    private final TaskReward rewards;
    private final TaskIcons icons;

    public TaskDefinition(String id, String categoryId, String name, TaskType taskType,
                          boolean autoReward, boolean resetAfterFinish,
                          List<String> requiredMissionIds, List<RequirementGroup> requirements,
                          TaskReward rewards, TaskIcons icons) {
        this.id = id;
        this.categoryId = categoryId;
        this.name = name;
        this.taskType = taskType;
        this.autoReward = autoReward;
        this.resetAfterFinish = resetAfterFinish;
        this.requiredMissionIds = requiredMissionIds;
        this.requirements = requirements;
        this.rewards = rewards;
        this.icons = icons;
    }

    public String getId() { return id; }
    public String getCategoryId() { return categoryId; }
    public String getName() { return name; }
    public TaskType getTaskType() { return taskType; }
    public boolean isAutoReward() { return autoReward; }
    public boolean isResetAfterFinish() { return resetAfterFinish; }
    public List<String> getRequiredMissionIds() { return requiredMissionIds; }
    public List<RequirementGroup> getRequirements() { return requirements; }
    public TaskReward getRewards() { return rewards; }
    public TaskIcons getIcons() { return icons; }

    public static class RequirementGroup {
        private final List<String> types;
        private final int amount;

        public RequirementGroup(List<String> types, int amount) {
            this.types = types;
            this.amount = amount;
        }

        public List<String> getTypes() { return types; }
        public int getAmount() { return amount; }
    }

    public static class TaskIcons {
        private final IconData notCompleted;
        private final IconData canComplete;
        private final IconData completed;

        public TaskIcons(IconData notCompleted, IconData canComplete, IconData completed) {
            this.notCompleted = notCompleted;
            this.canComplete = canComplete;
            this.completed = completed;
        }

        public IconData getNotCompleted() { return notCompleted; }
        public IconData getCanComplete() { return canComplete; }
        public IconData getCompleted() { return completed; }
    }

    public static class IconData {
        private final String material;
        private final String name;
        private final List<String> lore;
        private final boolean glow;

        public IconData(String material, String name, List<String> lore, boolean glow) {
            this.material = material;
            this.name = name;
            this.lore = lore;
            this.glow = glow;
        }

        public String getMaterial() { return material; }
        public String getName() { return name; }
        public List<String> getLore() { return lore; }
        public boolean isGlow() { return glow; }
    }
}
