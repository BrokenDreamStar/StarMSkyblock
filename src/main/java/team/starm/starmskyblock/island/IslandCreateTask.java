package team.starm.starmskyblock.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.util.ColorUtil;

import java.util.UUID;

public class IslandCreateTask extends BukkitRunnable {

    private final StarMSkyblock plugin;
    private final IslandManager islandManager;
    private final UUID playerUuid;
    private final String schematicId;
    private final String islandName;
    private final long startTime;

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
        // 异步执行耗时的创建操作
        try {
            // 发送开始创建消息
            sendMessageToPlayer("§a开始创建岛屿，请稍候...");

            // 1. 创建岛屿数据
            sendMessageToPlayer("§e正在分配岛屿位置...");
            Island island = islandManager.createIsland(playerUuid, schematicId, islandName);

            // 2. 获取世界（可能需要创建）
            sendMessageToPlayer("§e正在准备世界...");
            World world = plugin.getWorldManager().getOrCreateSkyblockWorld();
            World netherWorld = plugin.getWorldManager().getOrCreateSkyblockNether();
            World endWorld = plugin.getWorldManager().getOrCreateSkyblockEnd();

            int centerChunkX = island.getCenterChunkX();
            int centerChunkZ = island.getCenterChunkZ();
            int startX = centerChunkX * 16;
            int startZ = centerChunkZ * 16;
            int y = plugin.getConfigManager().getIslandHeight();

            // 岛屿中心点坐标
            int pasteX = startX + 8;
            int pasteZ = startZ + 8;

            // 3. 粘贴主世界结构
            sendMessageToPlayer("§e正在生成主世界结构...");
            String schematicName = plugin.getConfigManager().getNormalSchematicFileName(schematicId);
            boolean pasteSuccess = plugin.getSchematicManager().pasteSchematic(schematicName, world, pasteX, y, pasteZ);

            // 4. 粘贴下界结构（根据主世界结构文件名自动添加 _nether 后缀）
            sendMessageToPlayer("§e正在生成下界结构...");
            String netherSchematicName = plugin.getConfigManager().getNetherSchematicFileName(schematicId);
            plugin.getSchematicManager().pasteSchematic(netherSchematicName, netherWorld, pasteX, y, pasteZ);

            // 5. 粘贴末地结构（根据主世界结构文件名自动添加 _the_end 后缀）
            sendMessageToPlayer("§e正在生成末地结构...");
            String endSchematicName = plugin.getConfigManager().getEndSchematicFileName(schematicId);
            plugin.getSchematicManager().pasteSchematic(endSchematicName, endWorld, pasteX, y, pasteZ);

            // 计算创建用时
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // 6. 完成创建，发送结果消息
            if (pasteSuccess) {
                sendCompletionMessage(island, world, startX, startZ, y, duration, true);
            } else {
                sendCompletionMessage(island, world, startX, startZ, y, duration, false);
            }

        } catch (Exception e) {
            ColorUtil.consoleError("&c异步创建岛屿时发生错误！");
            e.printStackTrace();

            // 发送错误消息
            new BukkitRunnable() {
                @Override
                public void run() {
                    Player player = Bukkit.getPlayer(playerUuid);
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§c创建岛屿时发生意外错误，请联系管理员！");
                    }
                }
            }.runTask(plugin);
        }
    }

    /**
     * 向玩家发送消息
     */
    private void sendMessageToPlayer(String message) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    player.sendMessage(message);
                }
            }
        }.runTask(plugin);
    }

    /**
     * 发送创建完成消息（主线程安全）
     */
    private void sendCompletionMessage(Island island, World world, int startX, int startZ, int y,
            long duration, boolean success) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {

                    // 格式化时间
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

                    // 检查是否自动传送
                    if (plugin.getConfigManager().isTeleportOnCreate()) {
                        // 使用配置的偏移量进行传送（根据结构文件ID获取对应的偏移量）
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

                    // 广播给其他在线的管理员
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        if (onlinePlayer.hasPermission("skyblock.admin")
                                && !onlinePlayer.getUniqueId().equals(playerUuid)) {
                            onlinePlayer.sendMessage("§e玩家 " + player.getName() + " 已创建新岛屿 (ID: " + island.getId()
                                    + ")，用时 " + timeFormatted + "。");
                        }
                    }
                }
            }
        }.runTask(plugin);
    }
}