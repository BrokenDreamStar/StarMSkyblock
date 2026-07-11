package team.starm.starmskyblock.task;

import java.util.Collections;
import java.util.List;

/**
 * 任务章节（category）
 * <p>
 * 对应 {@code tasks/} 下的一个章节目录，聚合该章节内的所有 {@link TaskDefinition}。
 * 章节可声明 {@code required} 前置章节（通过 ID 引用），前置章节内所有任务全部 claim 后本章节解锁，
 * 详见 {@link TaskManager#isChapterUnlocked(UUID, String)}。
 * </p>
 */
public class TaskCategory {

    /** 章节目录名，作为唯一 ID（如 {@code Chapter1}） */
    private final String id;
    /** 章节序号，用于 /is task list 过滤与显示 */
    private final int chapterNumber;
    /** 章节显示名（取自 tasks.yml 的 name，缺省为目录名） */
    private final String name;
    /** 章节内任务列表，按扫描顺序排列 */
    private final List<TaskDefinition> tasks;
    /** 前置章节 ID 列表，全部任务 claim 后本章节才解锁 */
    private final List<String> requiredChapters;

    public TaskCategory(String id, int chapterNumber, String name, List<TaskDefinition> tasks) {
        this(id, chapterNumber, name, tasks, Collections.emptyList());
    }

    public TaskCategory(String id, int chapterNumber, String name, List<TaskDefinition> tasks, List<String> requiredChapters) {
        this.id = id;
        this.chapterNumber = chapterNumber;
        this.name = name;
        this.tasks = tasks;
        this.requiredChapters = requiredChapters;
    }

    public String getId() { return id; }
    public int getChapterNumber() { return chapterNumber; }
    public String getName() { return name; }
    public List<TaskDefinition> getTasks() { return tasks; }
    public List<String> getRequiredChapters() { return requiredChapters; }
}
