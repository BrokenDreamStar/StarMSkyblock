package team.starm.starmskyblock.task;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

import net.milkbowl.vault.economy.Economy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.HashSet;
import team.starm.starmskyblock.task.listener.BlockBreakTaskListener;
import team.starm.starmskyblock.task.listener.BlockPlaceTaskListener;
import team.starm.starmskyblock.task.listener.CraftingTaskListener;
import team.starm.starmskyblock.task.listener.EntityKillTaskListener;
import team.starm.starmskyblock.task.listener.FarmingTaskListener;
import team.starm.starmskyblock.task.listener.FishingTaskListener;
import team.starm.starmskyblock.task.listener.MoneyTaskListener;

/**
 * 任务进度管理器
 * <p>
 * 任务系统的核心：持有每玩家进度（{@link TaskProgress}）与脏标记，按配置中出现的
 * {@link TaskType} 选择性注册对应监听器，处理进度增量、手动提交、奖励领取与强制完成/重置。
 * 进度以 JSON 持久化到 players 表，玩家上线懒加载、下线/定期/停服时落盘。
 * </p>
 * <p>章节/任务解锁语义见 CLAUDE.md 任务系统小节：章节需前置章节全部 claim，任务需同章节前置任务全部 claim。</p>
 */
public class TaskManager {

    private final StarMSkyblock plugin;
    private final TaskConfigScanner taskConfig;
    private final PlayerRepository playerRepo;
    /** Gson 实例，负责 {@link TaskProgress} 与 JSON 互转 */
    private final Gson gson;

    /** 玩家 UUID -> (任务 ID -> {@link TaskProgress})，内层 Map 必须并发安全（见 loadPlayerProgress 注释） */
    private final Map<UUID, Map<String, TaskProgress>> playerProgress;
    /** 脏玩家集合，定期批量落盘时按此标记挑选 */
    private final Map<UUID, Boolean> dirtyPlayers;

    /** Gson 反射用类型标记：{@code Map<String, TaskProgress>} */
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

    /** 初始化：注册事件监听器并调度自动保存任务 */
    public void init() {
        registerListeners();
        scheduleAutoSave();
    }

    /** 按配置中实际出现的任务类型注册对应监听器，避免无任务类型时空跑事件 */
    private void registerListeners() {
        Collection<TaskType> activeTypes = taskConfig.getTypeIndex().keySet();

        if (activeTypes.contains(TaskType.BLOCK_BREAK)) {
            Bukkit.getPluginManager().registerEvents(
                    new BlockBreakTaskListener(this), plugin);
        }
        if (activeTypes.contains(TaskType.BLOCK_PLACE)) {
            Bukkit.getPluginManager().registerEvents(
                    new BlockPlaceTaskListener(this), plugin);
        }
        if (activeTypes.contains(TaskType.ENTITY_KILL)) {
            Bukkit.getPluginManager().registerEvents(
                    new EntityKillTaskListener(this), plugin);
        }
        if (activeTypes.contains(TaskType.FARMING)) {
            Bukkit.getPluginManager().registerEvents(
                    new FarmingTaskListener(this), plugin);
        }
        if (activeTypes.contains(TaskType.FISHING)) {
            Bukkit.getPluginManager().registerEvents(
                    new FishingTaskListener(this), plugin);
        }
        if (activeTypes.contains(TaskType.CRAFTING)) {
            Bukkit.getPluginManager().registerEvents(
                    new CraftingTaskListener(this), plugin);
        }
        if (activeTypes.contains(TaskType.EARN_MONEY)) {
            Bukkit.getPluginManager().registerEvents(
                    new MoneyTaskListener(this), plugin);
        }

        // 玩家上下线：上线懒加载进度，下线落盘并清理内存缓存
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                UUID uuid = event.getPlayer().getUniqueId();
                loadPlayerProgress(uuid);
            }

            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
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

    /** 清理已离线但仍驻留内存的玩家进度（防异常下线导致内存泄漏） */
    private void evictStaleProgress() {
        for (UUID uuid : new HashSet<>(playerProgress.keySet())) {
            if (Bukkit.getPlayer(uuid) == null) {
                savePlayerProgress(uuid);
                playerProgress.remove(uuid);
                dirtyPlayers.remove(uuid);
                completedTaskIds.remove(uuid);
            }
        }
    }

    /**
     * 从数据库加载玩家任务进度并预热 {@link #completedTaskIds}。
     * <p>内层进度 Map 强制转为 {@link ConcurrentHashMap}，避免主线程事件监听器写与
     * 异步保存线程 gson.toJson 迭代并发导致 CME / 丢数据 / 扩容死循环。JSON 损坏时静默重置为空并告警。</p>
     */
    public void loadPlayerProgress(UUID uuid) {
        String json = playerRepo.loadTasks(uuid);
        Map<String, TaskProgress> progress;
        try {
            progress = gson.fromJson(json, PROGRESS_MAP_TYPE);
        } catch (Exception e) {
            // JSON 损坏会静默返回空进度（相当于清空玩家任务进度），必须记录告警以便排查
            MessageUtil.consoleError("解析玩家 " + uuid + " 的任务进度 JSON 失败，已重置为空进度", e);
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

    /** 确保进度内层 Map 为并发安全实现（Gson 反序列化默认产出 HashMap） */
    private static Map<String, Integer> ensureConcurrent(Map<String, Integer> map) {
        if (map == null) return new ConcurrentHashMap<>();
        if (map instanceof ConcurrentHashMap) return map;
        return new ConcurrentHashMap<>(map);
    }

    /** 同步落盘单个玩家进度并清除脏标记（仅在落盘成功时清除，失败时保留以便重试） */
    public void savePlayerProgress(UUID uuid) {
        Map<String, TaskProgress> progress = playerProgress.get(uuid);
        if (progress == null) return;
        String json = gson.toJson(progress);
        if (playerRepo.saveTasks(uuid, json)) {
            dirtyPlayers.remove(uuid);
        }
        // 保存失败时保留脏标记，下次自动保存周期重试
    }

    /** 批量落盘所有脏玩家进度（由自动保存定时器调用）。保存成功才清除脏标记，失败保留以便下个周期重试。 */
    public void saveAllDirty() {
        if (dirtyPlayers.isEmpty()) return;
        Map<UUID, String> toSave = new HashMap<>();
        for (UUID uuid : new HashSet<>(dirtyPlayers.keySet())) {
            Map<String, TaskProgress> progress = playerProgress.get(uuid);
            if (progress != null) {
                toSave.put(uuid, gson.toJson(progress));
            }
        }
        if (!toSave.isEmpty()) {
            if (playerRepo.batchSaveTasks(toSave)) {
                // 仅保存成功时清除脏标记；失败则保留，下次自动保存周期重试
                for (UUID uuid : toSave.keySet()) {
                    dirtyPlayers.remove(uuid);
                }
            }
        } else {
            // 所有脏玩家均无进度数据（已离线清理），直接清除脏标记
            dirtyPlayers.clear();
        }
    }

    /** 全量落盘所有玩家进度（停服时调用）。无论成败均清除脏标记（进程即将退出，无重试价值）。 */
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

    /** 获取或创建玩家某任务的进度条目（不存在则插入空进度） */
    public TaskProgress getOrCreateProgress(UUID uuid, String taskId) {
        Map<String, TaskProgress> progressMap = playerProgress.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        return progressMap.computeIfAbsent(taskId, k -> new TaskProgress(new ConcurrentHashMap<>(), 0, false));
    }

    /** 返回玩家全部进度 Map（无数据返回空 Map） */
    public Map<String, TaskProgress> getPlayerProgressMap(UUID uuid) {
        return playerProgress.getOrDefault(uuid, Collections.emptyMap());
    }

    /**
     * 计入一次自然进度（等价于 {@link #incrementNaturalProgress} 的 isNatural=true 重载）。
     */
    public void incrementProgress(Player player, TaskType taskType, String key, int amount) {
        if (player == null) return;
        incrementNaturalProgress(player, taskType, key, amount, true);
    }

    /**
     * 计入任务进度，是所有事件监听器的统一入口。
     * <p>对 BLOCK_BREAK/BLOCK_PLACE 按材料名精确查找任务（命中空集合直接返回），
     * 其余类型按类型查找全部任务。逐任务校验 only-natural、已完成跳过、前置任务、章节解锁后累加进度，
     * 达成时发送完成通知。任一任务进度被修改则标记玩家脏。</p>
     *
     * @param isNatural 是否自然产生进度（非手动）；false 时跳过声明 only-natural 的任务
     */
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

    /**
     * 手动提交 ITEM 型任务物品。
     * <p>校验任务存在/类型/前置/章节解锁后，逐需求组清点背包材料（含药水类型匹配），
     * 不足则提示缺料；充足则扣除物品并写入等价进度。已 claim 或已完成则拒绝重复提交。</p>
     *
     * @return 提交成功返回 true
     */
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

    /**
     * 领取已完成任务的奖励。
     * <p>校验任务存在、已完成、未重复领取后，委托 {@link #claimReward} 发奖并标记 claim。</p>
     *
     * @return 领取成功返回 true
     */
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

    /**
     * 发放任务奖励（金币 + 物品 + 命令）。
     * <p>
     * 自检玩家在线态：金币用 {@code OfflinePlayer} 重载（对在线玩家同样工作）；物品仅在线时发放并掉落溢出；
     * 命令按 {@code server:}/{@code player:}/默认前缀解析，{@code player:} 仅在线时执行。
     * 名字源：在线用玩家名，离线用 OfflinePlayer 名（兜底 uuid 前 8 位）。
     * </p>
     * <p>状态变更（setClaimed/clear/markDirty/...）与消息发送由各调用方处理，本方法只负责奖励本身，
     * 对 {@code claimReward} 与 {@code giveForceCompleteRewards} 行为逐位等价。</p>
     *
     * @param uuid    玩家 UUID
     * @param rewards 奖励内容
     */
    private void grantRewards(UUID uuid, TaskReward rewards) {
        Player player = Bukkit.getPlayer(uuid);
        boolean online = player != null;
        String playerName;
        if (online) {
            playerName = player.getName();
        } else {
            String offlineName = Bukkit.getOfflinePlayer(uuid).getName();
            playerName = (offlineName != null) ? offlineName : uuid.toString().substring(0, 8);
        }

        if (rewards.money() > 0) {
            Economy econ = plugin.getEconomy();
            if (econ != null) {
                econ.depositPlayer(Bukkit.getOfflinePlayer(uuid), rewards.money());
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
    }

    /** 玩家领取奖励路径：发奖后标记 claim 并加入快路径跳过集合 */
    private boolean claimReward(Player player, TaskDefinition def, TaskProgress prog) {
        TaskReward rewards = def.getRewards();
        if (rewards.isEmpty()) {
            prog.setClaimed(true);
            prog.getProgress().clear();
            markDirty(player.getUniqueId());
            MessageUtil.send(player, "task.completed-no-reward", Map.of("task", def.getName()));
            return true;
        }

        grantRewards(player.getUniqueId(), rewards);


        prog.setClaimed(true);
        prog.getProgress().clear();
        markDirty(player.getUniqueId());
        // 加入快路径跳过集合
        Set<String> completed = completedTaskIds.get(player.getUniqueId());
        if (completed != null) completed.add(def.getId());
        MessageUtil.send(player, "task.reward-claimed", Map.of("task", def.getName()));
        return true;
    }

    /**
     * 校验任务的所有前置是否已满足。
     * <p>前置项可为章节 ID（要求该章节全部任务 claim）或单个任务 ID（要求该任务 claim）。</p>
     */
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

    /** 任务是否已解锁（无前置则恒真，否则要求所有前置已 claim） */
    public boolean isTaskUnlocked(UUID uuid, String taskId) {
        TaskDefinition def = taskConfig.getTask(taskId);
        if (def == null) return false;
        if (def.getRequiredMissionIds().isEmpty()) return true;
        return hasCompletedRequired(uuid, def);
    }

    /** 任务是否已领取奖励（基于 claimed 标记，不含"已完成未领取"状态） */
    public boolean isTaskCompleted(UUID uuid, String taskId) {
        Map<String, TaskProgress> progressMap = playerProgress.get(uuid);
        if (progressMap == null) return false;
        TaskProgress prog = progressMap.get(taskId);
        if (prog == null) return false;
        return prog.isClaimed();
    }

    /**
     * 章节是否已解锁。
     * <p>无前置章节则恒真；否则要求每个前置章节内的全部任务均已 claim。</p>
     */
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

    /** 玩家进度未加载时从数据库懒加载（用于事件热路径兜底） */
    private void ensureLoaded(UUID uuid) {
        if (!playerProgress.containsKey(uuid)) {
            loadPlayerProgress(uuid);
        }
    }

    /** 标记玩家进度已变更，待下次批量保存落盘 */
    private void markDirty(UUID uuid) {
        dirtyPlayers.put(uuid, Boolean.TRUE);
    }

    public TaskConfigScanner getTaskConfig() {
        return taskConfig;
    }

    /**
     * 校验物品是否符合需求组的药水基类型限定。
     * <p>无 potionType 限定时恒真；否则要求物品 meta 为 {@link PotionMeta} 且基类型名匹配。</p>
     */
    private static boolean matchesPotionRequirement(ItemStack item, String potionType) {
        if (potionType == null) return true;
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return false;
        if (meta.getBasePotionType() == null) return false;
        return meta.getBasePotionType().name().equalsIgnoreCase(potionType);
    }

    /**
     * 管理员强制完成任务（{@code /isadmin settask ... complete}）。
     * <p>直接将各需求组进度置满、发放奖励并标记 claim/notify。离线玩家发奖后移除内存缓存。</p>
     */
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

    /** 强制完成发奖路径：同步落盘并加入快路径集合，在线时发提示消息 */
    private void giveForceCompleteRewards(UUID uuid, TaskDefinition def, TaskProgress prog) {
        TaskReward rewards = def.getRewards();

        prog.setClaimed(true);
        prog.setNotified(true);
        prog.getProgress().clear();
        markDirty(uuid);
        savePlayerProgress(uuid);
        Set<String> completed = completedTaskIds.get(uuid);
        if (completed != null) completed.add(def.getId());

        Player player = Bukkit.getPlayer(uuid);
        boolean online = player != null;

        if (rewards.isEmpty()) {
            if (online) {
                MessageUtil.send(player, "task.admin-force-complete", Map.of("task", def.getName()));
            }
            return;
        }

        grantRewards(uuid, rewards);


        if (online) {
            MessageUtil.send(player, "task.admin-force-complete-with-rewards", Map.of("task", def.getName()));
        }
    }

    /**
     * 管理员重置任务进度（{@code /isadmin settask ... reset}）。
     * <p>移除该任务的进度条目并落盘；离线玩家处理完即移除内存缓存。</p>
     */
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
