package team.starm.starmskyblock.island;

import com.sk89q.worldedit.math.BlockVector3;
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
import team.starm.starmskyblock.config.SignConfigManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.List;
import java.util.UUID;

/**
 * 岛屿创建异步任务 —— 分阶段完成岛屿生成。
 * <p>
 * 设计为两阶段模式：
 * <ol>
 *   <li>异步阶段（async）：分配岛屿 ID、计算坐标、写入数据库（耗时短，无世界操作）</li>
 *   <li>同步阶段（sync）：粘贴主世界结构 → 下界结构 → 末地结构（每个阶段一个 tick，
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
            sendMessage("§a开始创建岛屿，请稍候...");

            // Phase 1 (async): 创建岛屿数据
            sendMessage("§e正在分配岛屿位置...");
            createdIsland = islandManager.createIsland(playerUuid, schematicId, islandName);

            // Phase 2 (sync): 粘贴结构 + 发送消息（主线程）
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        completeCreation();
                    } catch (Exception e) {
                        MessageUtil.consoleError("&c在主线程中完成岛屿创建时发生错误！");
                        e.printStackTrace();
                        sendMessage("§c创建岛屿时发生意外错误，请联系管理员！");
                    }
                }
            }.runTask(plugin);

        } catch (Exception e) {
            MessageUtil.consoleError("&c异步创建岛屿时发生错误！");
            e.printStackTrace();
            sendMessage("§c创建岛屿时发生意外错误，请联系管理员！");
        }
    }

    /**
     * 在主线程中完成岛屿创建：粘贴三个世界的结构文件。
     * 主世界 → 下界 → 末地通过 BukkitRunnable 链式延迟执行，
     * 每步之间间隔一个 tick 以避免单帧造成服务器卡顿。
     */
    private void completeCreation() {
        sendMessage("§e正在准备世界...");
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

        sendMessage("§e正在生成主世界结构...");
        String schematicName = plugin.getConfigManager().getNormalSchematicFileName(schematicId);
        boolean pasteSuccess = plugin.getSchematicManager().pasteSchematic(schematicName, world, pasteX, y, pasteZ);

        // 将下界和末地粘贴分散到后续tick，避免单帧卡顿
        String netherSchematicName = plugin.getConfigManager().getNetherSchematicFileName(schematicId);
        String endSchematicName = plugin.getConfigManager().getEndSchematicFileName(schematicId);

        new BukkitRunnable() {
            @Override
            public void run() {
                sendMessage("§e正在生成下界结构...");
                plugin.getSchematicManager().pasteSchematic(netherSchematicName, netherWorld, pasteX, y, pasteZ);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        sendMessage("§e正在生成末地结构...");
                        plugin.getSchematicManager().pasteSchematic(endSchematicName, endWorld, pasteX, y, pasteZ);

                        // 更新主世界告示牌文字
                        updateSigns(world, pasteX, y, pasteZ, schematicName);

                        long endTime = System.currentTimeMillis();
                        long duration = endTime - startTime;

                        sendCompletionMessage(createdIsland, world, startX, startZ, y, duration, pasteSuccess);
                    }
                }.runTask(plugin);
            }
        }.runTask(plugin);
    }

    /**
     * 更新结构中告示牌的文本内容。
     * 支持 PlaceholderAPI 变量替换，从 SignConfigManager 读取模板行。
     */
    private void updateSigns(World world, int pasteX, int pasteY, int pasteZ, String schematicName) {
        SignConfigManager signConfig = plugin.getSignConfigManager();
        if (signConfig == null || !signConfig.isEnabled()) {
            return;
        }

        List<String> lines = signConfig.getLines();
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
                            line = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, line);
                        }
                        sign.getSide(Side.FRONT).setLine(i, MessageUtil.colorize(line));
                    } else {
                        sign.getSide(Side.FRONT).setLine(i, "");
                    }
                }
                sign.update();
            }
        }
    }

    /** 向创建者发送消息（仅在玩家在线时） */
    private void sendMessage(String message) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
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
                player.sendMessage("§a================================");
                player.sendMessage("§a岛屿创建成功！");
                player.sendMessage("§a创建用时: §e" + timeFormatted);
                player.sendMessage("§a岛屿ID: §e" + island.getId());
                player.sendMessage("§a中心位置: §e" + startX + ", " + y + ", " + startZ);
                player.sendMessage("§a================================");
            } else {
                player.sendMessage("§c================================");
                player.sendMessage("§c岛屿创建成功，但结构文件加载失败！");
                player.sendMessage("§c创建用时: §e" + timeFormatted);
                player.sendMessage("§c岛屿ID: §e" + island.getId());
                player.sendMessage("§c中心位置: §e" + startX + ", " + y + ", " + startZ);
                player.sendMessage("§c================================");
            }

            if (plugin.getConfigManager().isTeleportOnCreate()) {
                ConfigManager config = plugin.getConfigManager();
                double[] offsets = config.getTeleportOffsetsBySchematicAndWorldType(schematicId,
                        Island.WorldType.NORMAL);
                double teleportX = startX + 8 + offsets[0];
                double teleportY = y + offsets[1];
                double teleportZ = startZ + 8 + offsets[2];
                Location spawnLocation = new Location(world, teleportX, teleportY, teleportZ);
                player.teleport(spawnLocation);
                player.sendMessage("§a已自动传送到你的岛屿！");
            }

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.hasPermission("skyblock.admin")
                        && !onlinePlayer.getUniqueId().equals(playerUuid)) {
                    onlinePlayer.sendMessage("§e玩家 " + player.getName() + " 已创建新岛屿 (ID: " + island.getId()
                            + ")，用时 " + timeFormatted + "。");
                }
            }
        }
    }
}
