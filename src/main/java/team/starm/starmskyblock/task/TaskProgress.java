package team.starm.starmskyblock.task;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class TaskProgress {

    @SerializedName("progress")
    private Map<String, Integer> progress;

    @SerializedName("completedCount")
    private int completedCount;

    @SerializedName("claimed")
    private boolean claimed;

    @SerializedName("notified")
    private boolean notified;

    public TaskProgress() {}

    public TaskProgress(Map<String, Integer> progress, int completedCount, boolean claimed) {
        this.progress = progress;
        this.completedCount = completedCount;
        this.claimed = claimed;
    }

    public TaskProgress(Map<String, Integer> progress, int completedCount, boolean claimed, boolean notified) {
        this.progress = progress;
        this.completedCount = completedCount;
        this.claimed = claimed;
        this.notified = notified;
    }

    public Map<String, Integer> getProgress() { return progress; }
    public void setProgress(Map<String, Integer> progress) { this.progress = progress; }

    public int getCompletedCount() { return completedCount; }
    public void setCompletedCount(int completedCount) { this.completedCount = completedCount; }

    public boolean isClaimed() { return claimed; }
    public void setClaimed(boolean claimed) { this.claimed = claimed; }

    public boolean isNotified() { return notified; }
    public void setNotified(boolean notified) { this.notified = notified; }

    public boolean isCompleted(TaskDefinition def) {
        if (progress == null) return false;
        for (TaskDefinition.RequirementGroup req : def.getRequirements()) {
            int total = 0;
            for (String type : req.getTypes()) {
                total += progress.getOrDefault(type.toUpperCase(), 0);
            }
            if (total < req.getAmount()) return false;
        }
        return true;
    }

    public double getProgressPercent(TaskDefinition def) {
        if (progress == null || def.getRequirements().isEmpty()) return 0;
        double totalWeight = 0;
        double completedWeight = 0;
        for (TaskDefinition.RequirementGroup req : def.getRequirements()) {
            totalWeight += req.getAmount();
            int total = 0;
            for (String type : req.getTypes()) {
                total += progress.getOrDefault(type.toUpperCase(), 0);
            }
            completedWeight += Math.min(total, req.getAmount());
        }
        return totalWeight > 0 ? completedWeight / totalWeight : 0;
    }
}
