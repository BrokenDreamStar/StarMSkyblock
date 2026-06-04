package team.starm.starmskyblock.task.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.task.TaskCategory;
import team.starm.starmskyblock.task.TaskDefinition;
import team.starm.starmskyblock.task.TaskType;
import team.starm.starmskyblock.task.reward.TaskReward;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskConfigManager {

    private final StarMSkyblock plugin;
    private File configFile;
    private FileConfiguration config;

    private Map<String, TaskCategory> categories;
    private Map<String, TaskDefinition> taskIndex;
    private Map<TaskType, List<TaskDefinition>> typeIndex;

    public TaskConfigManager(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        File tasksDir = new File(plugin.getDataFolder(), "tasks");
        if (!tasksDir.exists()) tasksDir.mkdirs();

        configFile = new File(tasksDir, "tasks.yml");
        if (!configFile.exists()) {
            plugin.saveResource("tasks/tasks.yml", false);
            plugin.saveResource("tasks/example.yml", false);
        }
        reload();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defaultStream = plugin.getResource("tasks/tasks.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
        }

        categories = new LinkedHashMap<>();
        taskIndex = new LinkedHashMap<>();
        typeIndex = new LinkedHashMap<>();

        ConfigurationSection categoriesSection = config.getConfigurationSection("categories");
        if (categoriesSection == null) {
            MessageUtil.consoleWarn("tasks.yml 中未找到 categories 配置");
            return;
        }

        for (String catId : categoriesSection.getKeys(false)) {
            ConfigurationSection catSection = categoriesSection.getConfigurationSection(catId);
            if (catSection == null) continue;

            String catName = catSection.getString("name", catId);
            int slot = catSection.getInt("slot", 0);

            List<TaskDefinition> tasks = new ArrayList<>();
            ConfigurationSection tasksSection = catSection.getConfigurationSection("tasks");
            if (tasksSection != null) {
                for (String taskId : tasksSection.getKeys(false)) {
                    ConfigurationSection taskSection = tasksSection.getConfigurationSection(taskId);
                    if (taskSection == null) continue;

                    TaskDefinition def = parseTask(taskId, catId, taskSection);
                    if (def != null) {
                        tasks.add(def);
                        taskIndex.put(taskId, def);
                        typeIndex.computeIfAbsent(def.getTaskType(), k -> new ArrayList<>()).add(def);
                    }
                }
            }

            categories.put(catId, new TaskCategory(catId, catName, slot, tasks));
        }

        MessageUtil.consolePrint("任务配置已加载，共 " + categories.size() + " 个分类，"
                + taskIndex.size() + " 个任务");
    }

    private TaskDefinition parseTask(String taskId, String catId, ConfigurationSection section) {
        String name = section.getString("name", taskId);
        String typeStr = section.getString("task-type");
        if (typeStr == null) {
            MessageUtil.consoleWarn("任务 " + taskId + " 缺少 task-type 字段");
            return null;
        }

        TaskType taskType;
        try {
            taskType = TaskType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            MessageUtil.consoleWarn("任务 " + taskId + " 的 task-type 无效: " + typeStr);
            return null;
        }

        boolean autoReward = section.getBoolean("auto-reward", false);
        boolean resetAfterFinish = section.getBoolean("reset-after-finish", true);
        List<String> requiredMissions = section.getStringList("required-missions");

        List<TaskDefinition.RequirementGroup> requirements = new ArrayList<>();
        ConfigurationSection reqSection = section.getConfigurationSection("requirements");
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

        List<ItemStack> itemRewards = new ArrayList<>();
        ConfigurationSection itemsSection = section.getConfigurationSection("rewards.items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection entry = itemsSection.getConfigurationSection(key);
                if (entry == null) continue;
                String materialName = entry.getString("type", "STONE");
                Material material = Material.matchMaterial(materialName);
                if (material == null) continue;
                int amount = entry.getInt("amount", 1);
                itemRewards.add(new ItemStack(material, amount));
            }
        }
        List<String> cmdRewards = section.getStringList("rewards.commands");
        TaskReward rewards = new TaskReward(itemRewards, cmdRewards);

        TaskDefinition.TaskIcons icons = parseIcons(section.getConfigurationSection("icons"));

        return new TaskDefinition(taskId, catId, name, taskType,
                autoReward, resetAfterFinish, requiredMissions,
                requirements, rewards, icons);
    }

    private TaskDefinition.TaskIcons parseIcons(ConfigurationSection iconSection) {
        if (iconSection == null) {
            return new TaskDefinition.TaskIcons(null, null, null);
        }
        return new TaskDefinition.TaskIcons(
                parseIconData(iconSection.getConfigurationSection("not-completed")),
                parseIconData(iconSection.getConfigurationSection("can-complete")),
                parseIconData(iconSection.getConfigurationSection("completed"))
        );
    }

    private TaskDefinition.IconData parseIconData(ConfigurationSection section) {
        if (section == null) return null;
        String material = section.getString("material", "PAPER");
        String name = section.getString("name");
        List<String> lore = section.getStringList("lore");
        boolean glow = section.getBoolean("glow", false);
        if (name == null && lore.isEmpty()) return null;
        return new TaskDefinition.IconData(material, name, lore, glow);
    }

    public Map<String, TaskCategory> getCategories() { return categories; }
    public Map<String, TaskDefinition> getTaskIndex() { return taskIndex; }
    public Map<TaskType, List<TaskDefinition>> getTypeIndex() { return typeIndex; }

    public TaskDefinition getTask(String id) { return taskIndex.get(id); }
    public List<TaskDefinition> getTasksByType(TaskType type) {
        return typeIndex.getOrDefault(type, Collections.emptyList());
    }
}
