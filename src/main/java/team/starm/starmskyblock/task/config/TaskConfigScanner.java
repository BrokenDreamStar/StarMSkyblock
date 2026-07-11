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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务配置扫描器
 * <p>
 * 启动时从 {@code plugins/StarMSkyblock/tasks/} 读取章节索引文件 {@code tasks.yml}
 * 与各章节目录下的任务 YAML，构建四套索引：按章节 ID、按任务 ID、按类型、按材料。
 * 内置任务文件在首次运行时从 jar 资源释放（已存在则跳过，不覆盖用户改动）。
 * </p>
 */
public class TaskConfigScanner {

    private final StarMSkyblock plugin;
    /** 任务根目录 {@code plugins/StarMSkyblock/tasks/} */
    private File tasksDir;
    /** 章节索引文件 {@code tasks/tasks.yml} */
    private File configFile;

    /** 章节目录名 -> {@link TaskCategory} */
    private Map<String, TaskCategory> categories;
    /** 任务 ID -> {@link TaskDefinition}（全量任务索引） */
    private Map<String, TaskDefinition> taskIndex;
    /** 任务类型 -> 该类型任务列表，供监听器按类型查找相关任务 */
    private Map<TaskType, List<TaskDefinition>> typeIndex;
    /** 材料->任务索引：用于 BLOCK_BREAK/BLOCK_PLACE 快速查找相关任务 */
    private Map<String, Set<TaskDefinition>> materialTaskIndex;

    /** 内置任务资源路径（相对 tasks/ 目录），首次启动时从 jar 释放 */
    private static final String[] BUILTIN_TASKS = {
        "tasks.yml",
        "Chapter1/task1_1.yml",
        "Chapter1/task1_2.yml",
        "Chapter1/task1_3.yml",
        "Chapter1/task1_4.yml",
        "Chapter1/task1_5.yml",
        "Chapter1/task1_6.yml",
        "Chapter2/task2_1.yml",
        "Chapter2/task2_2.yml",
        "Chapter2/task2_3.yml",
        "Chapter2/task2_4.yml",
        "Chapter2/task2_5.yml",
        "Chapter2/task2_6.yml",
        "Chapter2/task2_7.yml",
        "Chapter3/task3_1.yml",
        "Chapter3/task3_2.yml",
        "Chapter3/task3_3.yml",
        "Chapter3/task3_4.yml",
        "Chapter3/task3_5.yml",
        "Chapter3/task3_6.yml",
        "Chapter3/task3_7.yml",
        "Chapter3/task3_8.yml",
        "Chapter3/task3_9.yml",
        "Chapter3/task3_10.yml",
        "Chapter4/task4_1.yml",
        "Chapter4/task4_2.yml",
        "Chapter4/task4_3.yml",
        "Chapter4/task4_4.yml"
    };

    public TaskConfigScanner(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    /** 初始化目录、释放内置任务文件并执行首次扫描 */
    public void initialize() {
        tasksDir = new File(plugin.getDataFolder(), "tasks");
        if (!tasksDir.exists()) tasksDir.mkdirs();
        configFile = new File(tasksDir, "tasks.yml");
        extractBuiltinTasks();
        scan();
    }

    /** 从 jar 释放内置任务文件，已存在的保留不动以尊重用户改动 */
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
                MessageUtil.consoleError("创建任务文件失败: tasks/" + path, e);
            }
        }
    }

    /**
     * 扫描 tasks.yml 与各章节任务文件，重建四套索引。
     * <p>章节以 tasks.yml 的 {@code Chapters.<n>} 声明，含 {@code directory}、{@code name}、
     * {@code tasks}（任务文件名列表）、可选 {@code required}（前置章节 ID）。
     * 扫描完成后单独构建材料索引以加速方块类事件。</p>
     */
    public void scan() {
        categories = new LinkedHashMap<>();
        taskIndex = new LinkedHashMap<>();
        typeIndex = new LinkedHashMap<>();
        materialTaskIndex = new LinkedHashMap<>();

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

            List<String> requiredChapters = chapterSection.getStringList("required");

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
                categories.put(directory, new TaskCategory(directory, chapterNumber, chapterName, tasks, requiredChapters));
            }
        }

        MessageUtil.consolePrint("已加载任务配置 共" + categories.size() + "个章节 " + taskIndex.size() + "个任务");

        // 构建材料->任务索引，加速 BLOCK_BREAK/BLOCK_PLACE 事件处理
        for (TaskDefinition def : taskIndex.values()) {
            TaskType type = def.getTaskType();
            if (type != TaskType.BLOCK_BREAK && type != TaskType.BLOCK_PLACE) continue;
            for (TaskDefinition.RequirementGroup req : def.getRequirements()) {
                for (String mat : req.getTypes()) {
                    materialTaskIndex.computeIfAbsent(mat.toUpperCase(), k -> new HashSet<>()).add(def);
                }
            }
        }
    }

    /**
     * 解析单个任务 YAML 文件为 {@link TaskDefinition}。
     * <p>读取 name/description/task_type/task_required/only-natural/request/reward 等字段，
     * 任一必填字段缺失则记录告警并返回 null。{@code request} 段按多组需求解析（组间 AND、组内 OR）。</p>
     *
     * @param file           任务文件
     * @param categoryId     所属章节目录名
     * @param missionId      任务全 ID（目录/文件名）
     * @param missionNumber  章节内序号
     * @return 解析得到的任务定义，校验失败返回 null
     */
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
                String potionType = entry.getString("potion_type", null);
                if (!types.isEmpty()) {
                    requirements.add(new TaskDefinition.RequirementGroup(types, amount, potionType));
                }
            }
        }

        double rewardMoney = parseRewardMoney(config);
        List<String> rewardCommands = parseRewardCommands(config);
        List<TaskReward.ItemReward> rewardItems = parseRewardItems(config);

        TaskReward rewards = new TaskReward(rewardMoney, rewardCommands, rewardItems);

        return new TaskDefinition(missionId, categoryId, missionNumber, name, description,
                taskType, requiredMissions, onlyNatural, requirements, rewards);
    }

    /** 解析 task_required 字段，兼容列表与单字符串两种写法（{@code []} 视为空） */
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

    /** 读取 reward.money，无 reward 段返回 0 */
    private double parseRewardMoney(YamlConfiguration config) {
        ConfigurationSection rewardSection = config.getConfigurationSection("reward");
        if (rewardSection == null) return 0;
        return rewardSection.getDouble("money", 0);
    }

    /** 读取 reward.command 列表，无则返回空列表 */
    private List<String> parseRewardCommands(YamlConfiguration config) {
        ConfigurationSection rewardSection = config.getConfigurationSection("reward");
        if (rewardSection == null) return Collections.emptyList();
        List<String> cmds = rewardSection.getStringList("command");
        return cmds != null ? cmds : Collections.emptyList();
    }

    /** 读取 reward.item 物品奖励列表，逐条解析 material+amount */
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
    public Map<String, Set<TaskDefinition>> getMaterialTaskIndex() { return materialTaskIndex; }

    public TaskDefinition getTask(String id) { return taskIndex.get(id); }
    public List<TaskDefinition> getTasksByType(TaskType type) {
        return typeIndex.getOrDefault(type, Collections.emptyList());
    }
    /** 根据材料名获取关联的 BLOCK_BREAK/BLOCK_PLACE 任务集合 */
    public Set<TaskDefinition> getTasksByMaterial(String material) {
        return materialTaskIndex.getOrDefault(material, Collections.emptySet());
    }

    /** 按章节号 + 任务号定位任务（用于 /is task submit/claim） */
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

    /** 按章节号定位章节 */
    public TaskCategory getCategoryByChapterNumber(int chapterNumber) {
        for (TaskCategory cat : categories.values()) {
            if (cat.getChapterNumber() == chapterNumber) {
                return cat;
            }
        }
        return null;
    }

    /** 反查任务所属章节号，找不到返回 0 */
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

    /** 判断给定 ID 是否为章节 ID（用于 task_required 中区分章节级与任务级前置） */
    public boolean isChapterId(String id) {
        return categories.containsKey(id);
    }

    /** 返回章节内全部任务 ID（用于章节级前置解锁判定） */
    public List<String> getChapterTaskIds(String chapterId) {
        TaskCategory cat = categories.get(chapterId);
        if (cat == null) return Collections.emptyList();
        return cat.getTasks().stream().map(TaskDefinition::getId).toList();
    }
}
