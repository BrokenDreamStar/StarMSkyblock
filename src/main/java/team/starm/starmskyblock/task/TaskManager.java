package team.starm.starmskyblock.task;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.database.PlayerRepository;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.task.config.TaskConfigManager;
import team.starm.starmskyblock.task.reward.TaskReward;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TaskManager {

    private final StarMSkyblock plugin;
    private final TaskConfigManager taskConfig;
    private final PlayerRepository playerRepo;
    private final Gson gson;

    private final Map<UUID, Map<String, TaskProgress>> playerProgress;
    private final Map<UUID, Boolean> dirtyPlayers;

    private static final Type PROGRESS_MAP_TYPE = new TypeToken<Map<String, TaskProgress>>() {}.getType();

    public TaskManager(StarMSkyblock plugin, TaskConfigManager taskConfig) {
        this.plugin = plugin;
        this.taskConfig = taskConfig;
        this.playerRepo = plugin.getPlayerRepo();
        this.gson = new Gson();
        this.playerProgress = new ConcurrentHashMap<>();
        this.dirtyPlayers = new ConcurrentHashMap<>();
    }

    public void init() {
        registerListeners();
        scheduleAutoSave();
    }

    private void registerListeners() {
        Collection<TaskType> activeTypes = taskConfig.getTypeIndex().keySet();

        if (activeTypes.contains(TaskType.BLOCK_BREAK)) {
            Bukkit.getPluginManager().registerEvents(
                    new team.starm.starmskyblock.task.listener.BlockBreakTaskListener(this), plugin);
        }
        if (activeTypes.contains(TaskType.BLOCK_PLACE)) {
            Bukkit.getPluginManager().registerEvents(
                    new team.starm.starmskyblock.task.listener.BlockPlaceTaskListener(this), plugin);
        }
        if (activeTypes.contains(TaskType.ENTITY_KILL)) {
            Bukkit.getPluginManager().registerEvents(
                    new team.starm.starmskyblock.task.listener.EntityKillTaskListener(this), plugin);
        }
        if (activeTypes.contains(TaskType.FARMING)) {
            Bukkit.getPluginManager().registerEvents(
                    new team.starm.starmskyblock.task.listener.FarmingTaskListener(this), plugin);
        }
        if (activeTypes.contains(TaskType.FISHING)) {
            Bukkit.getPluginManager().registerEvents(
                    new team.starm.starmskyblock.task.listener.FishingTaskListener(this), plugin);
        }
        if (activeTypes.contains(TaskType.CRAFTING)) {
            Bukkit.getPluginManager().registerEvents(
                    new team.starm.starmskyblock.task.listener.CraftingTaskListener(this), plugin);
        }
        if (activeTypes.contains(TaskType.EARN_MONEY)) {
            Bukkit.getPluginManager().registerEvents(
                    new team.starm.starmskyblock.task.listener.MoneyTaskListener(this), plugin);
        }

        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                UUID uuid = event.getPlayer().getUniqueId();
                loadPlayerProgress(uuid);
            }

            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                UUID uuid = event.getPlayer().getUniqueId();
                savePlayerProgress(uuid);
                playerProgress.remove(uuid);
                dirtyPlayers.remove(uuid);
            }
        }, plugin);
    }

    private void scheduleAutoSave() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (UUID uuid : dirtyPlayers.keySet()) {
                savePlayerProgress(uuid);
            }
        }, 6000L, 6000L);
    }

    public void loadPlayerProgress(UUID uuid) {
        String json = playerRepo.loadTasks(uuid);
        Map<String, TaskProgress> progress;
        try {
            progress = gson.fromJson(json, PROGRESS_MAP_TYPE);
        } catch (Exception e) {
            progress = new HashMap<>();
        }
        if (progress == null) progress = new HashMap<>();
        playerProgress.put(uuid, progress);
    }

    public void savePlayerProgress(UUID uuid) {
        Map<String, TaskProgress> progress = playerProgress.get(uuid);
        if (progress == null) return;
        String json = gson.toJson(progress);
        playerRepo.saveTasks(uuid, json);
        dirtyPlayers.remove(uuid);
    }

    public void saveAll() {
        for (UUID uuid : playerProgress.keySet()) {
            savePlayerProgress(uuid);
        }
    }

    public TaskProgress getOrCreateProgress(UUID uuid, String taskId) {
        Map<String, TaskProgress> progressMap = playerProgress.computeIfAbsent(uuid, k -> new HashMap<>());
        return progressMap.computeIfAbsent(taskId, k -> new TaskProgress(new HashMap<>(), 0, false));
    }

    public Map<String, TaskProgress> getPlayerProgressMap(UUID uuid) {
        return playerProgress.getOrDefault(uuid, Collections.emptyMap());
    }

    public void incrementProgress(Player player, TaskType taskType, String key, int amount) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        List<TaskDefinition> tasks = taskConfig.getTasksByType(taskType);
        if (tasks.isEmpty()) return;

        ensureLoaded(uuid);

        Map<String, TaskProgress> progressMap = playerProgress.get(uuid);

        for (TaskDefinition def : tasks) {
            TaskProgress prog = progressMap.get(def.getId());
            if (prog == null) {
                prog = new TaskProgress(new HashMap<>(), 0, false);
                progressMap.put(def.getId(), prog);
            }

            if (!def.getRequiredMissionIds().isEmpty() && !hasCompletedRequired(uuid, def)) {
                continue;
            }

            if (prog.getCompletedCount() > 0 && prog.isClaimed() && !def.isResetAfterFinish()) {
                continue;
            }

            if (prog.getCompletedCount() > 0 && prog.isClaimed() && def.isResetAfterFinish()) {
                if (prog.isCompleted(def)) continue;
            }

            Map<String, Integer> pMap = prog.getProgress();
            if (pMap == null) {
                pMap = new HashMap<>();
                prog.setProgress(pMap);
            }

            String upperKey = key.toUpperCase();
            pMap.merge(upperKey, amount, Integer::sum);
            markDirty(uuid);

            if (!prog.isClaimed() && prog.isCompleted(def)) {
                if (def.isAutoReward()) {
                    claimReward(player, def, prog);
                } else {
                    MessageUtil.sendMessage(player, "&a任务 &e" + def.getName() + " &a已完成！使用 &e/is task claim "
                            + def.getId() + " &a领取奖励。");
                }
            }
        }
    }

    public boolean claimTask(Player player, String taskId) {
        UUID uuid = player.getUniqueId();
        ensureLoaded(uuid);

        TaskDefinition def = taskConfig.getTask(taskId);
        if (def == null) {
            MessageUtil.sendMessage(player, "&c任务不存在！");
            return false;
        }

        Map<String, TaskProgress> progressMap = playerProgress.get(uuid);
        if (progressMap == null) return false;

        TaskProgress prog = progressMap.get(taskId);
        if (prog == null || !prog.isCompleted(def)) {
            MessageUtil.sendMessage(player, "&c该任务尚未完成！");
            return false;
        }

        if (prog.isClaimed()) {
            MessageUtil.sendMessage(player, "&c该任务奖励已领取！");
            return false;
        }

        return claimReward(player, def, prog);
    }

    private boolean claimReward(Player player, TaskDefinition def, TaskProgress prog) {
        TaskReward rewards = def.getRewards();
        if (rewards.isEmpty()) {
            MessageUtil.sendMessage(player, "&c该任务没有奖励！");
            return true;
        }

        for (String cmd : rewards.commands()) {
            String parsed = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }

        if (rewards.items() != null && !rewards.items().isEmpty()) {
            PlayerInventory inv = player.getInventory();
            for (ItemStack item : rewards.items()) {
                if (item == null) continue;
                HashMap<Integer, ItemStack> leftover = inv.addItem(item.clone());
                for (ItemStack left : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
            }
        }

        prog.setClaimed(true);
        prog.setCompletedCount(prog.getCompletedCount() + 1);

        if (def.isResetAfterFinish()) {
            prog.setProgress(new HashMap<>());
        }

        markDirty(player.getUniqueId());
        MessageUtil.sendMessage(player, "&a✔ 已领取任务 &e" + def.getName() + " &a的奖励！");
        return true;
    }

    private boolean hasCompletedRequired(UUID uuid, TaskDefinition def) {
        Map<String, TaskProgress> progressMap = playerProgress.get(uuid);
        if (progressMap == null) return false;

        for (String reqId : def.getRequiredMissionIds()) {
            TaskProgress reqProg = progressMap.get(reqId);
            if (reqProg == null || reqProg.getCompletedCount() == 0) {
                return false;
            }
        }
        return true;
    }

    public boolean isTaskUnlocked(UUID uuid, String taskId) {
        TaskDefinition def = taskConfig.getTask(taskId);
        if (def == null) return false;
        if (def.getRequiredMissionIds().isEmpty()) return true;
        return hasCompletedRequired(uuid, def);
    }

    public boolean isTaskCompleted(UUID uuid, String taskId) {
        Map<String, TaskProgress> progressMap = playerProgress.get(uuid);
        if (progressMap == null) return false;
        TaskProgress prog = progressMap.get(taskId);
        if (prog == null) return false;
        return prog.getCompletedCount() > 0 && prog.isClaimed();
    }

    private void ensureLoaded(UUID uuid) {
        if (!playerProgress.containsKey(uuid)) {
            loadPlayerProgress(uuid);
        }
    }

    private void markDirty(UUID uuid) {
        dirtyPlayers.put(uuid, Boolean.TRUE);
    }

    public TaskConfigManager getTaskConfig() {
        return taskConfig;
    }
}
