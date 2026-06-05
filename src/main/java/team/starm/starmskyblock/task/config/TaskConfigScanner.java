package team.starm.starmskyblock.task.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.task.TaskCategory;
import team.starm.starmskyblock.task.TaskDefinition;
import team.starm.starmskyblock.task.TaskType;
import team.starm.starmskyblock.task.reward.TaskReward;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskConfigScanner {

    private final StarMSkyblock plugin;
    private File tasksDir;
    private File configFile;

    private Map<String, TaskCategory> categories;
    private Map<String, TaskDefinition> taskIndex;
    private Map<TaskType, List<TaskDefinition>> typeIndex;

    private static final String[] BUILTIN_TASKS = {
        "tasks.yml",
        "Chapter1/task1_1.yml",
        "Chapter1/task1_2.yml",
        "Chapter1/task1_3.yml",
        "Chapter1/task1_4.yml",
        "Chapter1/task1_5.yml",
        "Chapter1/task1_6.yml"
    };

    public TaskConfigScanner(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        tasksDir = new File(plugin.getDataFolder(), "tasks");
        if (!tasksDir.exists()) tasksDir.mkdirs();
        configFile = new File(tasksDir, "tasks.yml");
        extractBuiltinTasks();
        scan();
    }

    private void extractBuiltinTasks() {
        int created = 0;
        for (String path : BUILTIN_TASKS) {
            File target = new File(tasksDir, path);
            if (target.exists()) continue;
            target.getParentFile().mkdirs();
            try (InputStream is = plugin.getResource("tasks/" + path)) {
                if (is == null) {
                    MessageUtil.consoleWarn("找不到内置任务文件: tasks/" + path);
                    continue;
                }
                Files.copy(is, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                created++;
            } catch (IOException e) {
                MessageUtil.consoleWarn("创建任务文件失败: tasks/" + path);
                e.printStackTrace();
            }
        }
        if (created > 0) {
            MessageUtil.consolePrint("已创建任务文件 共 " + created + " 个文件");
        }
    }

    public void scan() {
        categories = new LinkedHashMap<>();
        taskIndex = new LinkedHashMap<>();
        typeIndex = new LinkedHashMap<>();

        if (!configFile.exists()) {
            MessageUtil.consoleWarn("未找到 tasks/tasks.yml 配置文件");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection chaptersSection = config.getConfigurationSection("Chapters");
        if (chaptersSection == null) {
            MessageUtil.consoleWarn("tasks.yml 中未找到 Chapters 配置");
            return;
        }

        for (String chapterKey : chaptersSection.getKeys(false)) {
            ConfigurationSection chapterSection = chaptersSection.getConfigurationSection(chapterKey);
            if (chapterSection == null) continue;

            int chapterNumber;
            try {
                chapterNumber = Integer.parseInt(chapterKey);
            } catch (NumberFormatException e) {
                MessageUtil.consoleWarn("章节 key 不是有效数字: " + chapterKey);
                continue;
            }

            String directory = chapterSection.getString("directory", "");
            if (directory.isEmpty()) {
                MessageUtil.consoleWarn("章节 " + chapterKey + " 缺少 directory 字段");
                continue;
            }

            String chapterName = chapterSection.getString("name", directory);
            List<String> taskNames = chapterSection.getStringList("tasks");
            if (taskNames.isEmpty()) continue;

            File chapterDir = new File(tasksDir, directory);
            if (!chapterDir.exists() || !chapterDir.isDirectory()) {
                MessageUtil.consoleWarn("章节目录不存在: " + directory);
                continue;
            }

            List<TaskDefinition> tasks = new ArrayList<>();
            int missionIndex = 1;

            for (String taskName : taskNames) {
                File missionFile = new File(chapterDir, taskName + ".yml");
                if (!missionFile.exists()) {
                    MessageUtil.consoleWarn("任务文件不存在: " + directory + "/" + taskName + ".yml");
                    continue;
                }

                String missionId = directory + "/" + taskName;
                TaskDefinition def = parseMissionFile(missionFile, directory, missionId, missionIndex);
                if (def != null) {
                    tasks.add(def);
                    taskIndex.put(missionId, def);
                    typeIndex.computeIfAbsent(def.getTaskType(), k -> new ArrayList<>()).add(def);
                    missionIndex++;
                }
            }

            if (!tasks.isEmpty()) {
                categories.put(directory, new TaskCategory(directory, chapterNumber, chapterName, tasks));
            }
        }

        MessageUtil.consolePrint("已加载任务配置 共" + categories.size() + "个章节 " + taskIndex.size() + "个任务");
    }

    private TaskDefinition parseMissionFile(File file, String categoryId, String missionId, int missionNumber) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String name = config.getString("name");
        if (name == null) {
            MessageUtil.consoleWarn("任务 " + missionId + " 缺少 name 字段");
            return null;
        }

        String description = config.getString("description", "");

        String typeStr = config.getString("task_type");
        if (typeStr == null) {
            MessageUtil.consoleWarn("任务 " + missionId + " 缺少 task_type 字段");
            return null;
        }
        TaskType taskType;
        try {
            taskType = TaskType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            MessageUtil.consoleWarn("任务 " + missionId + " 的 task_type 无效: " + typeStr);
            return null;
        }

        List<String> requiredMissions = parseRequiredMissions(config);

        boolean onlyNatural = config.getBoolean("only-natural", false);

        List<TaskDefinition.RequirementGroup> requirements = new ArrayList<>();
        ConfigurationSection reqSection = config.getConfigurationSection("request");
        if (reqSection != null) {
            for (String key : reqSection.getKeys(false)) {
                ConfigurationSection entry = reqSection.getConfigurationSection(key);
                if (entry == null) continue;
                List<String> types = entry.getStringList("types");
                int amount = entry.getInt("amount", 1);
                if (!types.isEmpty()) {
                    requirements.add(new TaskDefinition.RequirementGroup(types, amount));
                }
            }
        }

        List<String> rewardCommands = parseRewardCommands(config);
        List<TaskReward.ItemReward> rewardItems = parseRewardItems(config);

        TaskReward rewards = new TaskReward(rewardCommands, rewardItems);

        return new TaskDefinition(missionId, categoryId, missionNumber, name, description,
                taskType, requiredMissions, onlyNatural, requirements, rewards);
    }

    private List<String> parseRequiredMissions(YamlConfiguration config) {
        if (config.isList("task_required")) {
            return config.getStringList("task_required");
        }
        if (config.isString("task_required")) {
            String val = config.getString("task_required", "").trim();
            if (val.isEmpty() || val.equals("[]")) return Collections.emptyList();
            return List.of(val);
        }
        return Collections.emptyList();
    }

    private List<String> parseRewardCommands(YamlConfiguration config) {
        ConfigurationSection rewardSection = config.getConfigurationSection("reward");
        if (rewardSection == null) return Collections.emptyList();
        List<String> cmds = rewardSection.getStringList("command");
        return cmds != null ? cmds : Collections.emptyList();
    }

    private List<TaskReward.ItemReward> parseRewardItems(YamlConfiguration config) {
        ConfigurationSection rewardSection = config.getConfigurationSection("reward");
        if (rewardSection == null) return Collections.emptyList();
        List<Map<?, ?>> itemList = rewardSection.getMapList("item");
        if (itemList == null) return Collections.emptyList();
        List<TaskReward.ItemReward> items = new ArrayList<>();
        for (Map<?, ?> map : itemList) {
            Object matObj = map.get("material");
            if (matObj == null) continue;
            String material = matObj.toString();
            int amount = map.containsKey("amount") ? ((Number) map.get("amount")).intValue() : 1;
            items.add(new TaskReward.ItemReward(material, amount));
        }
        return items;
    }

    public Map<String, TaskCategory> getCategories() { return categories; }
    public Map<String, TaskDefinition> getTaskIndex() { return taskIndex; }
    public Map<TaskType, List<TaskDefinition>> getTypeIndex() { return typeIndex; }

    public TaskDefinition getTask(String id) { return taskIndex.get(id); }
    public List<TaskDefinition> getTasksByType(TaskType type) {
        return typeIndex.getOrDefault(type, Collections.emptyList());
    }

    public TaskDefinition getTaskByChapterAndMission(int chapterNumber, int missionNumber) {
        for (TaskCategory cat : categories.values()) {
            if (cat.getChapterNumber() == chapterNumber) {
                for (TaskDefinition def : cat.getTasks()) {
                    if (def.getMissionNumber() == missionNumber) {
                        return def;
                    }
                }
            }
        }
        return null;
    }

    public TaskCategory getCategoryByChapterNumber(int chapterNumber) {
        for (TaskCategory cat : categories.values()) {
            if (cat.getChapterNumber() == chapterNumber) {
                return cat;
            }
        }
        return null;
    }

    public int getChapterNumberByTaskId(String taskId) {
        TaskDefinition def = taskIndex.get(taskId);
        if (def == null) return 0;
        for (TaskCategory cat : categories.values()) {
            if (cat.getTasks().contains(def)) {
                return cat.getChapterNumber();
            }
        }
        return 0;
    }

    public boolean isChapterId(String id) {
        return categories.containsKey(id);
    }

    public List<String> getChapterTaskIds(String chapterId) {
        TaskCategory cat = categories.get(chapterId);
        if (cat == null) return Collections.emptyList();
        return cat.getTasks().stream().map(TaskDefinition::getId).toList();
    }
}
