package team.starm.starmskyblock.island;

import com.sk89q.worldedit.math.BlockVector3;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.message.MessageUtil;
import me.clip.placeholderapi.PlaceholderAPI;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 岛屿创建异步任务 -- 分阶段完成岛屿生成。
 * <p>
 * 设计为两阶段模式：
 * <ol>
 *   <li>异步阶段（async）：分配岛屿 ID、计算坐标、写入数据库（耗时短，无世界操作）</li>
 *   <li>同步阶段（sync）：粘贴主世界结构 -> 下界结构 -> 末地结构（每个阶段一个 tick，
 *       避免单帧卡死主线程）</li>
 * </ol>
 * 最后更新告示牌文字并传送玩家到新岛屿。
 */
public class IslandCreateTask extends BukkitRunnable {

    private final StarMSkyblock plugin;
    private final IslandManager islandManager;
    private final UUID playerUuid;
    private final String schematicId;
    private final String islandName;
    /** 创建开始时间戳（用于最终显示耗时） */
    private final long startTime;

    /** 在异步阶段创建的岛屿对象（volatile 保证跨线程可见性） */
    private volatile Island createdIsland;

    public IslandCreateTask(StarMSkyblock plugin, IslandManager islandManager,
            UUID playerUuid, String schematicId, String islandName) {
        this.plugin = plugin;
        this.islandManager = islandManager;
        this.playerUuid = playerUuid;
        this.schematicId = schematicId;
        this.islandName = islandName;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public void run() {
        try {
            sendKey("island.create.in-progress");

            // Phase 1 (async): 创建岛屿数据
            sendKey("island.create.allocating");
            createdIsland = islandManager.createIsland(playerUuid, schematicId, islandName);

            // Phase 2 (sync): 粘贴结构 + 发送消息（主线程）
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        completeCreation();
                    } catch (Exception e) {
                        MessageUtil.consoleError("在主线程中完成岛屿创建时发生错误！", e);
                        // 回滚：createIsland 已写入 DB 行 + 6 个内存索引，若 completeCreation
                        // 在世界加载/坐标计算等早期阶段抛异常，岛屿将停留于 DB+内存但无 schematic，
                        // 玩家因 islandsByOwner 占用而无法再次 /is create。deleteIsland 仅做 DB+内存
                        // 清理（不触碰世界方块，IslandDeleteTask 才负责清方块），适合此处回滚。
                        try {
                            islandManager.deleteIsland(createdIsland);
                            MessageUtil.consoleWarn("岛屿创建失败已回滚，岛屿 ID=" + createdIsland.getId()
                                    + "，玩家可重新创建岛屿。");
                        } catch (Exception rollbackEx) {
                            MessageUtil.consoleError("回滚失败岛屿时发生二次错误，岛屿 ID="
                                    + createdIsland.getId(), rollbackEx);
                        }
                        sendKey("island.create.unexpected-error");
                    }
                }
            }.runTask(plugin);

        } catch (Exception e) {
            MessageUtil.consoleError("异步创建岛屿时发生错误！", e);
            sendKey("island.create.unexpected-error");
        }
    }

    /**
     * 在主线程中完成岛屿创建：依次粘贴主世界 -> 下界 -> 末地结构。
     * 每步在独立 tick 中执行以避免单帧卡顿，任一步失败则记录日志并跳过后续阶段，
     * 但仍会发送完成消息（success=false），调用方可据此感知部分失败。
     */
    private void completeCreation() {
        sendKey("island.create.preparing-world");
        World world = plugin.getWorldManager().getOrCreateSkyblockWorld();
        World netherWorld = plugin.getWorldManager().getOrCreateSkyblockNether();
        World endWorld = plugin.getWorldManager().getOrCreateSkyblockEnd();

        int centerChunkX = createdIsland.getCenterChunkX();
        int centerChunkZ = createdIsland.getCenterChunkZ();
        int startX = centerChunkX * 16;
        int startZ = centerChunkZ * 16;
        int y = plugin.getConfigManager().getIslandHeight();
        int pasteX = startX + 8;
        int pasteZ = startZ + 8;

        String schematicName = plugin.getConfigManager().getNormalSchematicFileName(schematicId);
        String netherSchematicName = plugin.getConfigManager().getNetherSchematicFileName(schematicId);
        String endSchematicName = plugin.getConfigManager().getEndSchematicFileName(schematicId);

        // 阶段 1：主世界（同步执行，复用当前 tick）
        sendKey("island.create.generating-normal");
        boolean normalOk = pasteSchematicSync(schematicName, world, pasteX, y, pasteZ,
                MessageUtil.format("dimension.normal"));

        // 阶段 2：下界（下一 tick），失败则跳过末地
        scheduleNext(() -> {
            if (!normalOk) {
                finishCreation(world, startX, startZ, y, schematicName, false);
                return;
            }
            sendKey("island.create.generating-nether");
            boolean netherOk = pasteSchematicSync(netherSchematicName, netherWorld, pasteX, y, pasteZ,
                    MessageUtil.format("dimension.nether"));

            // 阶段 3：末地（下一 tick）
            scheduleNext(() -> {
                if (!netherOk) {
                    finishCreation(world, startX, startZ, y, schematicName, false);
                    return;
                }
                sendKey("island.create.generating-end");
                boolean endOk = pasteSchematicSync(endSchematicName, endWorld, pasteX, y, pasteZ,
                        MessageUtil.format("dimension.end"));
                finishCreation(world, startX, startZ, y, schematicName, endOk);
            });
        });
    }

    /** 粘贴单个 schematic 并返回是否成功；失败时记录一行错误日志，便于排障。 */
    private boolean pasteSchematicSync(String schematicName, World targetWorld, int x, int y, int z, String stageLabel) {
        if (targetWorld == null) {
            MessageUtil.consoleError("岛屿创建阶段 [" + stageLabel + "] 跳过：目标世界未加载。岛屿 ID=" + createdIsland.getId());
            return false;
        }
        try {
            boolean ok = plugin.getSchematicManager().pasteSchematic(schematicName, targetWorld, x, y, z);
            if (!ok) {
                MessageUtil.consoleError("岛屿创建阶段 [" + stageLabel + "] 粘贴失败：schematic=" + schematicName
                        + "，岛屿 ID=" + createdIsland.getId());
            }
            return ok;
        } catch (Exception e) {
            MessageUtil.consoleError("岛屿创建阶段 [" + stageLabel + "] 粘贴异常：schematic=" + schematicName
                    + "，岛屿 ID=" + createdIsland.getId(), e);
            return false;
        }
    }

    /** 在下一 tick 执行任务，捕获异常避免调度链断裂。 */
    private void scheduleNext(Runnable task) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    MessageUtil.consoleError("岛屿创建调度链中发生错误，岛屿 ID=" + createdIsland.getId(), e);
                    sendKey("island.create.unexpected-error");
                }
            }
        }.runTask(plugin);
    }

    /** 末地粘贴完成（或被跳过）后的收尾：保存基线、更新告示牌、发送完成消息。 */
    private void finishCreation(World world, int startX, int startZ, int y, String schematicName, boolean success) {
        try {
            plugin.getLevelManager().saveBaseline(createdIsland, schematicId);
            updateSigns(world, startX + 8, y, startZ + 8, schematicName);
        } catch (Exception e) {
            MessageUtil.consoleError("岛屿创建收尾阶段发生错误，岛屿 ID=" + createdIsland.getId(), e);
        }
        long duration = System.currentTimeMillis() - startTime;
        sendCompletionMessage(createdIsland, world, startX, startZ, y, duration, success);
    }

    /**
     * 更新结构中告示牌的文本内容。
     * 支持 PlaceholderAPI 变量替换，从 ConfigManager 读取告示牌模板行。
     */
    private void updateSigns(World world, int pasteX, int pasteY, int pasteZ, String schematicName) {
        ConfigManager config = plugin.getConfigManager();
        if (config == null || !config.isSignEnabled()) {
            return;
        }

        List<String> lines = config.getSignLines();
        if (lines.isEmpty()) {
            return;
        }

        List<BlockVector3> signOffsets = plugin.getSchematicManager().getSignOffsets(schematicName);
        if (signOffsets == null || signOffsets.isEmpty()) {
            return;
        }

        Player player = Bukkit.getPlayer(playerUuid);
        boolean papiAvailable = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

        for (BlockVector3 offset : signOffsets) {
            Block block = world.getBlockAt(pasteX + offset.x(), pasteY + offset.y(), pasteZ + offset.z());
            if (block.getState() instanceof Sign sign) {
                for (int i = 0; i < 4; i++) {
                    if (i < lines.size()) {
                        String line = lines.get(i);
                        if (line == null) {
                            line = "";
                        }
                        if (papiAvailable && player != null) {
                            line = PlaceholderAPI.setPlaceholders(player, line);
                        }
                        sign.getSide(Side.FRONT).line(i, MessageUtil.parse(line));
                    } else {
                        sign.getSide(Side.FRONT).line(i, Component.empty());
                    }
                }
                sign.update();
            }
        }
    }

    /** 向创建者发送 i18n 消息（仅在玩家在线时；响应 -s 静默标志） */
    private void sendKey(String key) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            MessageUtil.send(player, key);
        }
    }

    /** 向创建者发送带占位符的 i18n 消息（仅在玩家在线时；响应 -s 静默标志） */
    private void sendKey(String key, Map<String, ?> args) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            MessageUtil.send(player, key, args);
        }
    }

    /** 向玩家发送创建完成信息，并根据配置自动传送回城 */
    private void sendCompletionMessage(Island island, World world, int startX, int startZ, int y,
            long duration, boolean success) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {

            String timeFormatted;
            if (duration < 1000) {
                timeFormatted = duration + "ms";
            } else {
                timeFormatted = String.format("%.2fs", duration / 1000.0);
            }

            if (success) {
                MessageUtil.send(player, "island.create.divider");
                MessageUtil.send(player, "island.create.success-header");
                MessageUtil.send(player, "island.create.success-time", Map.of("time", timeFormatted));
                MessageUtil.send(player, "island.create.success-id", Map.of("id", island.getId()));
                MessageUtil.send(player, "island.create.success-position",
                        Map.of("x", startX, "y", y, "z", startZ));
                MessageUtil.send(player, "island.create.divider");
            } else {
                MessageUtil.send(player, "island.create.divider-error");
                MessageUtil.send(player, "island.create.partial-success");
                MessageUtil.send(player, "island.create.success-time-error", Map.of("time", timeFormatted));
                MessageUtil.send(player, "island.create.success-id-error", Map.of("id", island.getId()));
                MessageUtil.send(player, "island.create.success-position-error",
                        Map.of("x", startX, "y", y, "z", startZ));
                MessageUtil.send(player, "island.create.divider-error");
            }

            if (plugin.getConfigManager().isTeleportOnCreate()) {
                ConfigManager config = plugin.getConfigManager();
                double[] offsets = config.getTeleportOffsetsBySchematicAndWorldType(schematicId,
                        Island.WorldType.NORMAL);
                double teleportX = startX + 8 + offsets[0];
                double teleportY = y + offsets[1];
                double teleportZ = startZ + 8 + offsets[2];
                Location spawnLocation = new Location(world, teleportX, teleportY, teleportZ,
                        (float) offsets[3], (float) offsets[4]);
                player.teleport(spawnLocation);
                MessageUtil.send(player, "island.create.teleported");

            }

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.hasPermission("skyblock.admin")
                        && !onlinePlayer.getUniqueId().equals(playerUuid)) {
                    MessageUtil.send(onlinePlayer, "island.create.admin-broadcast",
                            Map.of("player", player.getName(), "id", island.getId(), "time", timeFormatted));
                }
            }
        }
    }
}
