package team.starm.starmskyblock.task;

import java.util.Collections;
import java.util.List;

public class TaskCategory {

    private final String id;
    private final int chapterNumber;
    private final String name;
    private final List<TaskDefinition> tasks;
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
