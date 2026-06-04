package team.starm.starmskyblock.task;

import java.util.List;

public class TaskCategory {

    private final String id;
    private final String name;
    private final int slot;
    private final List<TaskDefinition> tasks;

    public TaskCategory(String id, String name, int slot, List<TaskDefinition> tasks) {
        this.id = id;
        this.name = name;
        this.slot = slot;
        this.tasks = tasks;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getSlot() { return slot; }
    public List<TaskDefinition> getTasks() { return tasks; }
}
