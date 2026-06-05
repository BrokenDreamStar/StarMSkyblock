package team.starm.starmskyblock.task;

import java.util.List;

public class TaskCategory {

    private final String id;
    private final int chapterNumber;
    private final String name;
    private final List<TaskDefinition> tasks;

    public TaskCategory(String id, int chapterNumber, String name, List<TaskDefinition> tasks) {
        this.id = id;
        this.chapterNumber = chapterNumber;
        this.name = name;
        this.tasks = tasks;
    }

    public String getId() {
        return id;
    }

    public int getChapterNumber() {
        return chapterNumber;
    }

    public String getName() {
        return name;
    }

    public List<TaskDefinition> getTasks() {
        return tasks;
    }
}
