package team.starm.starmskyblock.task;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * 玩家在某任务上的进度记录
 * <p>
 * 由 Gson 序列化为 JSON 存入 players 表。进度以 {@code Map<String, Integer>} 记录，
 * key 为类型名大写（材料/实体类型/{@code money}），带药水限定时追加 {@code :POTIONTYPE} 后缀。
 * 该后缀必须与 {@link TaskDefinition.RequirementGroup} 中的 key 构造逻辑保持一致，
 * 否则进度写入与完成判定会对不上。
 * </p>
 */
public class TaskProgress {

    /** 各类型累计进度，key 格式见类注释 */
    @SerializedName("progress")
    private Map<String, Integer> progress;

    /** 已完成次数（当前仅 0/1 语义） */
    @SerializedName("completedCount")
    private int completedCount;

    /** 是否已领取奖励 */
    @SerializedName("claimed")
    private boolean claimed;

    /** 是否已发送完成通知（防重复提示） */
    @SerializedName("notified")
    private boolean notified;

    /** Gson 反序列化用无参构造 */
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

    /**
     * 判断该任务所有需求组是否达成
     * <p>每组内对所有候选类型求和，与 {@link TaskDefinition.RequirementGroup#getAmount()} 比较；
     * 任一组未达标返回 false。key 构造与进度写入保持一致（大写 + 可选药水后缀）。</p>
     *
     * @param def 任务定义，提供需求组列表
     * @return 全部需求组达标返回 true
     */
    public boolean isCompleted(TaskDefinition def) {
        if (progress == null) return false;
        for (TaskDefinition.RequirementGroup req : def.getRequirements()) {
            int total = 0;
            for (String type : req.getTypes()) {
                String key = type.toUpperCase();
                if (req.getPotionType() != null) {
                    key = key + ":" + req.getPotionType().toUpperCase();
                }
                total += progress.getOrDefault(key, 0);
            }
            if (total < req.getAmount()) return false;
        }
        return true;
    }

    /**
     * 计算任务整体完成百分比（0~1）
     * <p>以各需求组目标数量为权重，每组实际累计取 min(已有, 目标)，加权平均后归一。</p>
     *
     * @param def 任务定义，提供需求组列表
     * @return 0~1 之间的完成比例，无需求组时返回 0
     */
    public double getProgressPercent(TaskDefinition def) {
        if (progress == null || def.getRequirements().isEmpty()) return 0;
        double totalWeight = 0;
        double completedWeight = 0;
        for (TaskDefinition.RequirementGroup req : def.getRequirements()) {
            totalWeight += req.getAmount();
            int total = 0;
            for (String type : req.getTypes()) {
                String key = type.toUpperCase();
                if (req.getPotionType() != null) {
                    key = key + ":" + req.getPotionType().toUpperCase();
                }
                total += progress.getOrDefault(key, 0);
            }
            completedWeight += Math.min(total, req.getAmount());
        }
        return totalWeight > 0 ? completedWeight / totalWeight : 0;
    }
}
