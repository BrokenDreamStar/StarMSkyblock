package team.starm.starmskyblock.task;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.database.PlayerRepository;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.task.config.TaskConfigScanner;
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
    private final TaskConfigScanner taskConfig;
    private final PlayerRepository playerRepo;
    private final Gson gson;

    private final Map<UUID, Map<String, TaskProgress>> playerProgress;
    private final Map<UUID, Boolean> dirtyPlayers;

    private static final Type PROGRESS_MAP_TYPE = new TypeToken<Map<String, TaskProgress>>() {}.getType();

    public TaskManager(StarMSkyblock plugin, TaskConfigScanner taskConfig) {
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
        incrementNaturalProgress(player, taskType, key, amount, true);
    }

    public void incrementNaturalProgress(Player player, TaskType taskType, String key, int amount, boolean isNatural) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        List<TaskDefinition> tasks = taskConfig.getTasksByType(taskType);
        if (tasks.isEmpty()) return;

        ensureLoaded(uuid);

        Map<String, TaskProgress> progressMap = playerProgress.get(uuid);

        for (TaskDefinition def : tasks) {
            if (def.isOnlyNatural() && !isNatural) continue;

            TaskProgress prog = progressMap.get(def.getId());
            if (prog == null) {
                prog = new TaskProgress(new HashMap<>(), 0, false);
                progressMap.put(def.getId(), prog);
            }

            if (prog.isClaimed()) continue;

            if (!def.getRequiredMissionIds().isEmpty() && !hasCompletedRequired(uuid, def)) {
                continue;
            }

            Map<String, Integer> pMap = prog.getProgress();
            if (pMap == null) {
                pMap = new HashMap<>();
                prog.setProgress(pMap);
            }

            String upperKey = key.toUpperCase();
            pMap.merge(upperKey, amount, Integer::sum);
            markDirty(uuid);

            if (!prog.isClaimed() && !prog.isNotified() && prog.isCompleted(def)) {
                prog.setNotified(true);
                markDirty(uuid);
                int ch = taskConfig.getChapterNumberByTaskId(def.getId());
                int ms = def.getMissionNumber();
                MessageUtil.sendMessage(player, "&a任务 &e" + def.getName() + " &a已完成！使用 &e/is task claim "
                        + ch + " " + ms + " &a领取奖励。");
            }
        }
    }

    public boolean submitItems(Player player, String taskId) {
        UUID uuid = player.getUniqueId();
        ensureLoaded(uuid);

        TaskDefinition def = taskConfig.getTask(taskId);
        if (def == null) {
            MessageUtil.sendMessage(player, "&c任务不存在！");
            return false;
        }
        if (def.getTaskType() != TaskType.ITEM) {
            MessageUtil.sendMessage(player, "&c该任务类型不支持物品提交！");
            return false;
        }

        if (!def.getRequiredMissionIds().isEmpty() && !hasCompletedRequired(uuid, def)) {
            MessageUtil.sendMessage(player, "&c请先完成前置任务！");
            return false;
        }

        Map<String, TaskProgress> progressMap = playerProgress.get(uuid);
        TaskProgress prog = progressMap.get(taskId);
        if (prog != null && prog.isClaimed()) {
            MessageUtil.sendMessage(player, "&c该任务已完成！");
            return false;
        }

        PlayerInventory inv = player.getInventory();

        for (TaskDefinition.RequirementGroup req : def.getRequirements()) {
            int hasAmount = 0;
            for (String type : req.getTypes()) {
                Material mat = Material.matchMaterial(type);
                if (mat == null) continue;
                for (ItemStack item : inv.all(mat).values()) {
                    if (item != null && matchesPotionRequirement(item, req.getPotionType())) {
                        hasAmount += item.getAmount();
                    }
                }
            }
            if (hasAmount < req.getAmount()) {
                String typesStr = String.join(", ", req.getTypes());
                MessageUtil.sendMessage(player, "&c物品不足！需要 &e" + typesStr + " &cx " + req.getAmount());
                return false;
            }
        }

        for (TaskDefinition.RequirementGroup req : def.getRequirements()) {
            int remaining = req.getAmount();
            for (String type : req.getTypes()) {
                if (remaining <= 0) break;
                Material mat = Material.matchMaterial(type);
                if (mat == null) continue;
                for (ItemStack item : inv.all(mat).values()) {
                    if (remaining <= 0) break;
                    if (item == null) continue;
                    if (!matchesPotionRequirement(item, req.getPotionType())) continue;
                    int toRemove = Math.min(remaining, item.getAmount());
                    item.setAmount(item.getAmount() - toRemove);
                    remaining -= toRemove;
                }
            }
        }

        if (prog == null) {
            prog = new TaskProgress(new HashMap<>(), 0, false);
            progressMap.put(taskId, prog);
        }

        for (TaskDefinition.RequirementGroup req : def.getRequirements()) {
            String key = req.getTypes().get(0);
            if (req.getPotionType() != null) {
                key = key + ":" + req.getPotionType().toUpperCase();
            }
            prog.getProgress().merge(key, req.getAmount(), Integer::sum);
        }

        markDirty(uuid);
        int ch = taskConfig.getChapterNumberByTaskId(def.getId());
        int ms = def.getMissionNumber();
        MessageUtil.sendMessage(player, "&a✔ 已提交物品！使用 &e/is task claim " + ch + " " + ms + " &a领取奖励。");
        return true;
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
            prog.setClaimed(true);
            markDirty(player.getUniqueId());
            MessageUtil.sendMessage(player, "&a✔ 任务 &e" + def.getName() + " &a已完成！");
            return true;
        }

        for (TaskReward.ItemReward itemReward : rewards.items()) {
            Material mat = Material.matchMaterial(itemReward.material());
            if (mat == null) continue;
            ItemStack item = new ItemStack(mat, itemReward.amount());
            Map<Integer, ItemStack> remaining = player.getInventory().addItem(item);
            for (ItemStack drop : remaining.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }

        for (String cmd : rewards.commands()) {
            if (cmd == null || cmd.isEmpty()) continue;
            String parsed = cmd.replace("%player_name%", player.getName())
                    .replace("%player%", player.getName());

            if (parsed.startsWith("server:")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed.substring("server:".length()).trim());
            } else if (parsed.startsWith("player:")) {
                player.performCommand(parsed.substring("player:".length()).trim());
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }

        prog.setClaimed(true);
        markDirty(player.getUniqueId());
        MessageUtil.sendMessage(player, "&a✔ 已领取任务 &e" + def.getName() + " &a的奖励！");
        return true;
    }

    private boolean hasCompletedRequired(UUID uuid, TaskDefinition def) {
        Map<String, TaskProgress> progressMap = playerProgress.get(uuid);
        if (progressMap == null) return false;

        for (String reqId : def.getRequiredMissionIds()) {
            if (taskConfig.isChapterId(reqId)) {
                for (String chapterTaskId : taskConfig.getChapterTaskIds(reqId)) {
                    TaskProgress reqProg = progressMap.get(chapterTaskId);
                    if (reqProg == null || !reqProg.isClaimed()) {
                        return false;
                    }
                }
            } else {
                TaskProgress reqProg = progressMap.get(reqId);
                if (reqProg == null || !reqProg.isClaimed()) {
                    return false;
                }
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
        return prog.isClaimed();
    }

    private void ensureLoaded(UUID uuid) {
        if (!playerProgress.containsKey(uuid)) {
            loadPlayerProgress(uuid);
        }
    }

    private void markDirty(UUID uuid) {
        dirtyPlayers.put(uuid, Boolean.TRUE);
    }

    public TaskConfigScanner getTaskConfig() {
        return taskConfig;
    }

    private static boolean matchesPotionRequirement(ItemStack item, String potionType) {
        if (potionType == null) return true;
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return false;
        if (meta.getBasePotionType() == null) return false;
        return meta.getBasePotionType().name().equalsIgnoreCase(potionType);
    }

    public void adminForceComplete(UUID uuid, String taskId) {
        ensureLoaded(uuid);

        TaskDefinition def = taskConfig.getTask(taskId);
        if (def == null) return;

        TaskProgress prog = getOrCreateProgress(uuid, taskId);
        for (TaskDefinition.RequirementGroup req : def.getRequirements()) {
            for (String type : req.getTypes()) {
                String key = type.toUpperCase();
                if (req.getPotionType() != null) {
                    key = key + ":" + req.getPotionType().toUpperCase();
                }
                prog.getProgress().put(key, req.getAmount());
            }
        }
        prog.setClaimed(true);
        prog.setNotified(true);
        markDirty(uuid);
        savePlayerProgress(uuid);

        if (Bukkit.getPlayer(uuid) == null) {
            playerProgress.remove(uuid);
        }
    }

    public void adminResetTask(UUID uuid, String taskId) {
        ensureLoaded(uuid);

        Map<String, TaskProgress> progressMap = playerProgress.get(uuid);
        if (progressMap != null) {
            progressMap.remove(taskId);
            markDirty(uuid);
            savePlayerProgress(uuid);
        }

        if (Bukkit.getPlayer(uuid) == null) {
            playerProgress.remove(uuid);
        }
    }
}
