package team.starm.starmskyblock.task;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.database.PlayerRepository;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.message.NameTranslator;
import team.starm.starmskyblock.task.config.TaskConfigScanner;
import team.starm.starmskyblock.task.reward.TaskReward;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * 每玩家已完成(claimed)任务 ID 集合,用于 {@link #incrementNaturalProgress}
     * 热路径快速跳过已完成任务,避免在 BLOCK_BREAK/ENTITY_KILL 等高频事件中
     * 反复迭代全量任务列表、调用 {@code isChapterUnlocked} 等开销。
     */
    private final Map<UUID, Set<String>> completedTaskIds = new ConcurrentHashMap<>();

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
                completedTaskIds.remove(uuid);
            }
        }, plugin);
    }

    private void scheduleAutoSave() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveAllDirty, 6000L, 6000L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::evictStaleProgress, 12000L, 12000L);
    }

    private void evictStaleProgress() {
        for (UUID uuid : new java.util.HashSet<>(playerProgress.keySet())) {
            if (Bukkit.getPlayer(uuid) == null) {
                savePlayerProgress(uuid);
                playerProgress.remove(uuid);
                dirtyPlayers.remove(uuid);
                completedTaskIds.remove(uuid);
            }
        }
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

        // 内层 Map 必须使用 ConcurrentHashMap，避免主线程事件监听器写、
        // 异步保存线程 gson.toJson 迭代时发生 CME / 丢数据 / 扩容死循环。
        Map<String, TaskProgress> concurrent = new ConcurrentHashMap<>(progress.size());
        for (Map.Entry<String, TaskProgress> entry : progress.entrySet()) {
            TaskProgress tp = entry.getValue();
            if (tp != null) {
                tp.setProgress(ensureConcurrent(tp.getProgress()));
            }
            concurrent.put(entry.getKey(), tp);
        }
        playerProgress.put(uuid, concurrent);

        // 预热 completedTaskIds 集合,方便 incrementNaturalProgress 热路径快速跳过
        Set<String> completed = ConcurrentHashMap.newKeySet();
        for (Map.Entry<String, TaskProgress> entry : concurrent.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isClaimed()) {
                completed.add(entry.getKey());
            }
        }
        completedTaskIds.put(uuid, completed);
    }

    private static Map<String, Integer> ensureConcurrent(Map<String, Integer> map) {
        if (map == null) return new ConcurrentHashMap<>();
        if (map instanceof ConcurrentHashMap) return map;
        return new ConcurrentHashMap<>(map);
    }

    public void savePlayerProgress(UUID uuid) {
        Map<String, TaskProgress> progress = playerProgress.get(uuid);
        if (progress == null) return;
        String json = gson.toJson(progress);
        playerRepo.saveTasks(uuid, json);
        dirtyPlayers.remove(uuid);
    }

    public void saveAllDirty() {
        if (dirtyPlayers.isEmpty()) return;
        Map<UUID, String> toSave = new HashMap<>();
        for (UUID uuid : new java.util.HashSet<>(dirtyPlayers.keySet())) {
            Map<String, TaskProgress> progress = playerProgress.get(uuid);
            if (progress != null) {
                toSave.put(uuid, gson.toJson(progress));
            }
            dirtyPlayers.remove(uuid);
        }
        if (!toSave.isEmpty()) {
            playerRepo.batchSaveTasks(toSave);
        }
    }

    public void saveAll() {
        Map<UUID, String> toSave = new HashMap<>();
        for (Map.Entry<UUID, Map<String, TaskProgress>> entry : playerProgress.entrySet()) {
            UUID uuid = entry.getKey();
            toSave.put(uuid, gson.toJson(entry.getValue()));
            dirtyPlayers.remove(uuid);
        }
        if (!toSave.isEmpty()) {
            playerRepo.batchSaveTasks(toSave);
        }
    }

    public TaskProgress getOrCreateProgress(UUID uuid, String taskId) {
        Map<String, TaskProgress> progressMap = playerProgress.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        return progressMap.computeIfAbsent(taskId, k -> new TaskProgress(new ConcurrentHashMap<>(), 0, false));
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

        String upperKey = key.toUpperCase();
        Collection<TaskDefinition> tasks;
        if (taskType == TaskType.BLOCK_BREAK || taskType == TaskType.BLOCK_PLACE) {
            tasks = taskConfig.getTasksByMaterial(upperKey);
            if (tasks.isEmpty()) return;
        } else {
            List<TaskDefinition> typeTasks = taskConfig.getTasksByType(taskType);
            if (typeTasks.isEmpty()) return;
            tasks = typeTasks;
        }

        ensureLoaded(uuid);

        Map<String, TaskProgress> progressMap = playerProgress.get(uuid);
        Set<String> completed = completedTaskIds.get(uuid);
        if (completed == null) {
            completed = ConcurrentHashMap.newKeySet();
            completedTaskIds.put(uuid, completed);
        }
        boolean updated = false;

        for (TaskDefinition def : tasks) {
            if (def.isOnlyNatural() && !isNatural) continue;

            // 快路径:已 claim 的任务直接跳过,避免 per-task isChapterUnlocked/
            // hasCompletedRequired 等嵌套遍历开销
            String defId = def.getId();
            if (completed.contains(defId)) continue;

            TaskProgress prog = progressMap.get(defId);
            if (prog == null) {
                prog = new TaskProgress(new ConcurrentHashMap<>(), 0, false);
                progressMap.put(defId, prog);
            }

            if (prog.isClaimed()) continue;

            if (!def.getRequiredMissionIds().isEmpty() && !hasCompletedRequired(uuid, def)) {
                continue;
            }

            String categoryId = def.getCategoryId();
            if (!isChapterUnlocked(uuid, categoryId)) {
                continue;
            }

            Map<String, Integer> pMap = prog.getProgress();
            if (pMap == null) {
                pMap = new ConcurrentHashMap<>();
                prog.setProgress(pMap);
            }

            pMap.merge(upperKey, amount, Integer::sum);
            updated = true;

            if (!prog.isNotified() && prog.isCompleted(def)) {
                prog.setNotified(true);
                int ch = taskConfig.getChapterNumberByTaskId(defId);
                int ms = def.getMissionNumber();
                MessageUtil.send(player, "task.completed-message",
                        Map.of("task", def.getName(), "chapter", ch, "mission", ms));
            }
        }

        if (updated) {
            markDirty(uuid);
        }
    }

    public boolean submitItems(Player player, String taskId) {
        UUID uuid = player.getUniqueId();
        ensureLoaded(uuid);

        TaskDefinition def = taskConfig.getTask(taskId);
        if (def == null) {
            MessageUtil.send(player, "task.not-found-simple");
            return false;
        }
        if (def.getTaskType() != TaskType.ITEM) {
            MessageUtil.send(player, "task.submit.unsupported-type");
            return false;
        }

        if (!def.getRequiredMissionIds().isEmpty() && !hasCompletedRequired(uuid, def)) {
            MessageUtil.send(player, "task.required-tasks-not-completed");
            return false;
        }

        String categoryId = def.getCategoryId();
        if (!isChapterUnlocked(uuid, categoryId)) {
            MessageUtil.send(player, "task.chapter-not-unlocked");
            return false;
        }

        Map<String, TaskProgress> progressMap = playerProgress.get(uuid);
        TaskProgress prog = progressMap.get(taskId);
        if (prog != null && (prog.isClaimed() || prog.isCompleted(def))) {
            MessageUtil.send(player, "task.already-completed");
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
                Component typesComponent = Component.empty();
                for (int i = 0; i < req.getTypes().size(); i++) {
                    if (i > 0) typesComponent = typesComponent.append(Component.text(", "));
                    typesComponent = typesComponent.append(
                            NameTranslator.translatable(req.getTypes().get(i)).color(NamedTextColor.YELLOW));
                }
                MessageUtil.sendMessage(player, Component.textOfChildren(
                        MessageUtil.parse(MessageUtil.format("task.submit.insufficient-header")),
                        typesComponent,
                        MessageUtil.parse(MessageUtil.format("task.submit.insufficient-separator")),
                        Component.text(req.getAmount(), NamedTextColor.RED)
                ));
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
            prog = new TaskProgress(new ConcurrentHashMap<>(), 0, false);
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
        MessageUtil.send(player, "task.submit.success", Map.of("chapter", ch, "mission", ms));
        return true;
    }

    public boolean claimTask(Player player, String taskId) {
        UUID uuid = player.getUniqueId();
        ensureLoaded(uuid);

        TaskDefinition def = taskConfig.getTask(taskId);
        if (def == null) {
            MessageUtil.send(player, "task.not-found-simple");
            return false;
        }

        Map<String, TaskProgress> progressMap = playerProgress.get(uuid);
        if (progressMap == null) return false;

        TaskProgress prog = progressMap.get(taskId);
        if (prog == null || !prog.isCompleted(def)) {
            MessageUtil.send(player, "task.not-completed");
            return false;
        }

        if (prog.isClaimed()) {
            MessageUtil.send(player, "task.reward-already-claimed");
            return false;
        }

        return claimReward(player, def, prog);
    }

    private boolean claimReward(Player player, TaskDefinition def, TaskProgress prog) {
        TaskReward rewards = def.getRewards();
        if (rewards.isEmpty()) {
            prog.setClaimed(true);
            prog.getProgress().clear();
            markDirty(player.getUniqueId());
            MessageUtil.send(player, "task.completed-no-reward", Map.of("task", def.getName()));
            return true;
        }

        if (rewards.money() > 0) {
            net.milkbowl.vault.economy.Economy econ = plugin.getEconomy();
            if (econ != null) {
                econ.depositPlayer(player, rewards.money());
            }
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
        prog.getProgress().clear();
        markDirty(player.getUniqueId());
        // 加入快路径跳过集合
        Set<String> completed = completedTaskIds.get(player.getUniqueId());
        if (completed != null) completed.add(def.getId());
        MessageUtil.send(player, "task.reward-claimed", Map.of("task", def.getName()));
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

    public boolean isChapterUnlocked(UUID uuid, String categoryId) {
        TaskCategory cat = taskConfig.getCategories().get(categoryId);
        if (cat == null || cat.getRequiredChapters().isEmpty()) return true;
        for (String reqId : cat.getRequiredChapters()) {
            TaskCategory reqCat = taskConfig.getCategories().get(reqId);
            if (reqCat == null) continue;
            for (TaskDefinition def : reqCat.getTasks()) {
                if (!isTaskCompleted(uuid, def.getId())) return false;
            }
        }
        return true;
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

        giveForceCompleteRewards(uuid, def, prog);

        if (Bukkit.getPlayer(uuid) == null) {
            playerProgress.remove(uuid);
        }
    }

    private void giveForceCompleteRewards(UUID uuid, TaskDefinition def, TaskProgress prog) {
        TaskReward rewards = def.getRewards();

        prog.setClaimed(true);
        prog.setNotified(true);
        prog.getProgress().clear();
        markDirty(uuid);
        savePlayerProgress(uuid);
        Set<String> completed = completedTaskIds.get(uuid);
        if (completed != null) completed.add(def.getId());

        boolean online = false;
        Player player = Bukkit.getPlayer(uuid);
        String playerName = null;
        if (player != null) {
            online = true;
            playerName = player.getName();
        } else {
            playerName = Bukkit.getOfflinePlayer(uuid).getName();
            if (playerName == null) playerName = uuid.toString().substring(0, 8);
        }

        if (rewards.isEmpty()) {
            if (online) {
                MessageUtil.send(player, "task.admin-force-complete", Map.of("task", def.getName()));
            }
            return;
        }

        if (rewards.money() > 0) {
            net.milkbowl.vault.economy.Economy econ = plugin.getEconomy();
            if (econ != null) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                econ.depositPlayer(offlinePlayer, rewards.money());
            }
        }

        if (online) {
            for (TaskReward.ItemReward itemReward : rewards.items()) {
                Material mat = Material.matchMaterial(itemReward.material());
                if (mat == null) continue;
                ItemStack item = new ItemStack(mat, itemReward.amount());
                Map<Integer, ItemStack> remaining = player.getInventory().addItem(item);
                for (ItemStack drop : remaining.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }

        for (String cmd : rewards.commands()) {
            if (cmd == null || cmd.isEmpty()) continue;
            String parsed = cmd.replace("%player_name%", playerName)
                    .replace("%player%", playerName);

            if (parsed.startsWith("server:")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed.substring("server:".length()).trim());
            } else if (parsed.startsWith("player:")) {
                if (online) {
                    player.performCommand(parsed.substring("player:".length()).trim());
                }
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }

        if (online) {
            MessageUtil.send(player, "task.admin-force-complete-with-rewards", Map.of("task", def.getName()));
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
