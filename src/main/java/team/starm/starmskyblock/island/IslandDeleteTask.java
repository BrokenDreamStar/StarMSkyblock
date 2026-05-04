package team.starm.starmskyblock.island;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.util.ColorUtil;

import java.util.UUID;

public class IslandDeleteTask extends BukkitRunnable {

    private final StarMSkyblock plugin;
    private final IslandManager islandManager;
    private final Island island;
    private final UUID playerUuid;
    private final int deleteCount;
    private final int maxDeleteTimes;

    public IslandDeleteTask(StarMSkyblock plugin, IslandManager islandManager,
                           Island island, UUID playerUuid, int deleteCount, int maxDeleteTimes) {
        this.plugin = plugin;
        this.islandManager = islandManager;
        this.island = island;
        this.playerUuid = playerUuid;
        this.deleteCount = deleteCount;
        this.maxDeleteTimes = maxDeleteTimes;
    }

    @Override
    public void run() {
        // 异步执行耗时的清理操作
        try {
            // 1. 清理方块 (FAWE) - 这是最耗时的操作
            World world = plugin.getWorldManager().getSkyblockWorld();
            World netherWorld = plugin.getWorldManager().getSkyblockNether();
            World endWorld = plugin.getWorldManager().getSkyblockEnd();

            int radius = island.getRadius();
            int minChunkX = island.getCenterChunkX() - radius;
            int maxChunkX = island.getCenterChunkX() + radius;
            int minChunkZ = island.getCenterChunkZ() - radius;
            int maxChunkZ = island.getCenterChunkZ() + radius;

            int minX = minChunkX * 16;
            int maxX = maxChunkX * 16 + 15;
            int minZ = minChunkZ * 16;
            int maxZ = maxChunkZ * 16 + 15;

            // 异步清理方块
            if (world != null) {
                plugin.getSchematicManager().clearArea(world, minX, world.getMinHeight(), minZ, maxX,
                        world.getMaxHeight(), maxZ);
            }
            if (netherWorld != null) {
                plugin.getSchematicManager().clearArea(netherWorld, minX, netherWorld.getMinHeight(), minZ, maxX,
                        netherWorld.getMaxHeight(), maxZ);
            }
            if (endWorld != null) {
                plugin.getSchematicManager().clearArea(endWorld, minX, endWorld.getMinHeight(), minZ, maxX,
                        endWorld.getMaxHeight(), maxZ);
            }

            // 2. 在主线程中清理实体（必须在主线程执行）
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        if (world != null) {
                            world.getEntities().stream().filter(e -> !(e instanceof Player)).forEach(e -> {
                                int eChunkX = e.getLocation().getChunk().getX();
                                int eChunkZ = e.getLocation().getChunk().getZ();
                                if (eChunkX >= minChunkX && eChunkX <= maxChunkX && eChunkZ >= minChunkZ && eChunkZ <= maxChunkZ) {
                                    e.remove();
                                }
                            });
                        }
                        if (netherWorld != null) {
                            netherWorld.getEntities().stream().filter(e -> !(e instanceof Player)).forEach(e -> {
                                int eChunkX = e.getLocation().getChunk().getX();
                                int eChunkZ = e.getLocation().getChunk().getZ();
                                if (eChunkX >= minChunkX && eChunkX <= maxChunkX && eChunkZ >= minChunkZ && eChunkZ <= maxChunkZ) {
                                    e.remove();
                                }
                            });
                        }
                        if (endWorld != null) {
                            endWorld.getEntities().stream().filter(e -> !(e instanceof Player)).forEach(e -> {
                                int eChunkX = e.getLocation().getChunk().getX();
                                int eChunkZ = e.getLocation().getChunk().getZ();
                                if (eChunkX >= minChunkX && eChunkX <= maxChunkX && eChunkZ >= minChunkZ && eChunkZ <= maxChunkZ) {
                                    e.remove();
                                }
                            });
                        }

                        // 3. 数据库操作（可以在异步线程中执行）
                        boolean success = islandManager.deleteIslandFromDatabase(island);
                        if (success) {
                            // 增加玩家删除次数
                            islandManager.incrementDeleteCount(playerUuid);

                            // 发送完成消息
                            Player player = Bukkit.getPlayer(playerUuid);
                            if (player != null && player.isOnline()) {
                                player.sendMessage("§a岛屿已成功删除！你已删除 " + (deleteCount + 1) + "/" + maxDeleteTimes + " 次岛屿。");
                            }

                            // 广播给其他在线的管理员
                            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                if (onlinePlayer.hasPermission("skyblock.admin") && !onlinePlayer.getUniqueId().equals(playerUuid)) {
                                    onlinePlayer.sendMessage("§e玩家 " + (player != null ? player.getName() : playerUuid.toString()) + " 已删除其岛屿。");
                                }
                            }
                        } else {
                            // 发送错误消息
                            Player player = Bukkit.getPlayer(playerUuid);
                            if (player != null && player.isOnline()) {
                                player.sendMessage("§c删除岛屿数据时发生错误，请联系管理员！");
                            }
                        }
                    } catch (Exception e) {
                        ColorUtil.consoleError("&c在主线程中清理实体时发生错误！");
                        e.printStackTrace();

                        // 发送错误消息
                        Player player = Bukkit.getPlayer(playerUuid);
                        if (player != null && player.isOnline()) {
                            player.sendMessage("§c删除岛屿时发生意外错误，请联系管理员！");
                        }
                    }
                }
            }.runTask(plugin);

        } catch (Exception e) {
            ColorUtil.consoleError("&c异步删除岛屿时发生错误！");
            e.printStackTrace();

            // 在主线程中发送错误消息
            new BukkitRunnable() {
                @Override
                public void run() {
                    Player player = Bukkit.getPlayer(playerUuid);
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§c删除岛屿时发生意外错误，请联系管理员！");
                    }
                }
            }.runTask(plugin);
        }
    }
}
