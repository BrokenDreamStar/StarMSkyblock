package team.starm.starmskyblock.island;

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
import team.starm.starmskyblock.util.ColorUtil;

import java.util.List;
import java.util.UUID;

public class IslandCreateTask extends BukkitRunnable {

    private final StarMSkyblock plugin;
    private final IslandManager islandManager;
    private final UUID playerUuid;
    private final String schematicId;
    private final String islandName;
    private final long startTime;

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
                        ColorUtil.consoleError("&c在主线程中完成岛屿创建时发生错误！");
                        e.printStackTrace();
                        sendMessage("§c创建岛屿时发生意外错误，请联系管理员！");
                    }
                }
            }.runTask(plugin);

        } catch (Exception e) {
            ColorUtil.consoleError("&c异步创建岛屿时发生错误！");
            e.printStackTrace();
            sendMessage("§c创建岛屿时发生意外错误，请联系管理员！");
        }
    }

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

        sendMessage("§e正在生成下界结构...");
        String netherSchematicName = plugin.getConfigManager().getNetherSchematicFileName(schematicId);
        plugin.getSchematicManager().pasteSchematic(netherSchematicName, netherWorld, pasteX, y, pasteZ);

        sendMessage("§e正在生成末地结构...");
        String endSchematicName = plugin.getConfigManager().getEndSchematicFileName(schematicId);
        plugin.getSchematicManager().pasteSchematic(endSchematicName, endWorld, pasteX, y, pasteZ);

        // 更新主世界告示牌文字
        updateSigns(world, pasteX, y, pasteZ, schematicName);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        sendCompletionMessage(createdIsland, world, startX, startZ, y, duration, pasteSuccess);
    }

    private void updateSigns(World world, int pasteX, int pasteY, int pasteZ, String schematicName) {
        SignConfigManager signConfig = plugin.getSignConfigManager();
        if (signConfig == null || !signConfig.isEnabled()) {
            return;
        }

        List<String> lines = signConfig.getLines();
        if (lines.isEmpty()) {
            return;
        }

        int[] bounds = plugin.getSchematicManager().getSchematicBounds(schematicName, pasteX, pasteY, pasteZ);
        if (bounds == null) {
            return;
        }

        int minX = bounds[0];
        int minY = bounds[1];
        int minZ = bounds[2];
        int maxX = bounds[3];
        int maxY = bounds[4];
        int maxZ = bounds[5];

        Player player = Bukkit.getPlayer(playerUuid);
        boolean papiAvailable = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    Block block = world.getBlockAt(bx, by, bz);
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
                                sign.getSide(Side.FRONT).setLine(i, ColorUtil.colorize(line));
                            } else {
                                sign.getSide(Side.FRONT).setLine(i, "");
                            }
                        }
                        sign.update();
                    }
                }
            }
        }
    }

    private void sendMessage(String message) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }

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
