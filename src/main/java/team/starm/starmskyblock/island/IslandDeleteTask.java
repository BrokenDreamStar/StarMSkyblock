package team.starm.starmskyblock.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.UUID;

/**
 * 岛屿删除异步任务 —— 清空三个世界的方块 → 清理实体 → 删除数据库记录。
 * <p>
 * 分两步执行：
 * <ol>
 *   <li>异步阶段：使用 WorldEdit 批量清空主世界/下界/末地的方块（耗时操作）</li>
 *   <li>同步阶段：在主线程清理非玩家实体 + 删除数据库</li>
 * </ol>
 * 实体清理必须在主线程执行以避免并发修改问题。
 */
public class IslandDeleteTask extends BukkitRunnable {

    private final StarMSkyblock plugin;
    private final IslandManager islandManager;
    private final Island island;
    private final UUID playerUuid;
    /** 当前已删除次数（用于消息提示） */
    private final int deleteCount;
    /** 允许的最大删除次数（配置值） */
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

    /** 异步执行方块清空 → 调度主线程清理实体和数据库 */
    @Override
    public void run() {
        try {
            // 1. 异步阶段：用 WorldEdit 批量清空方块（脱离主线程避免卡服）
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

            // 2. 同步阶段：在主线程传送玩家 + 清理非玩家实体 + 删除数据库记录
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        java.util.List<World> worlds = new java.util.ArrayList<>();
                        if (world != null) worlds.add(world);
                        if (netherWorld != null) worlds.add(netherWorld);
                        if (endWorld != null) worlds.add(endWorld);

                        Location spawnLoc = world != null ? world.getSpawnLocation() : null;

                        BoundingBox islandBox = new BoundingBox(minX, -64, minZ, maxX + 1, 320, maxZ + 1);
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (islandBox.contains(p.getLocation().toVector())) {
                                if (spawnLoc != null) p.teleport(spawnLoc);
                                p.sendMessage("§c你的岛屿已被删除，你已被传送至主城！");
                            }
                        }

                        if (world != null) {
                            BoundingBox box = new BoundingBox(minX, world.getMinHeight(), minZ, maxX + 1, world.getMaxHeight(), maxZ + 1);
                            world.getNearbyEntities(box).stream()
                                    .filter(e -> !(e instanceof Player))
                                    .forEach(e -> e.remove());
                        }
                        if (netherWorld != null) {
                            BoundingBox box = new BoundingBox(minX, netherWorld.getMinHeight(), minZ, maxX + 1, netherWorld.getMaxHeight(), maxZ + 1);
                            netherWorld.getNearbyEntities(box).stream()
                                    .filter(e -> !(e instanceof Player))
                                    .forEach(e -> e.remove());
                        }
                        if (endWorld != null) {
                            BoundingBox box = new BoundingBox(minX, endWorld.getMinHeight(), minZ, maxX + 1, endWorld.getMaxHeight(), maxZ + 1);
                            endWorld.getNearbyEntities(box).stream()
                                    .filter(e -> !(e instanceof Player))
                                    .forEach(e -> e.remove());
                        }

                        boolean success = islandManager.deleteIslandFromDatabase(island);
                        if (success) {
                            islandManager.incrementDeleteCount(playerUuid);

                            Player player = Bukkit.getPlayer(playerUuid);
                            if (player != null && player.isOnline()) {
                                player.sendMessage("§a岛屿已成功删除！你已删除 " + (deleteCount + 1) + "/" + maxDeleteTimes + " 次岛屿。");
                            }

                            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                if (onlinePlayer.hasPermission("skyblock.admin") && !onlinePlayer.getUniqueId().equals(playerUuid)) {
                                    onlinePlayer.sendMessage("§e玩家 " + (player != null ? player.getName() : playerUuid.toString()) + " 已删除其岛屿。");
                                }
                            }
                        } else {
                            Player player = Bukkit.getPlayer(playerUuid);
                            if (player != null && player.isOnline()) {
                                player.sendMessage("§c删除岛屿数据时发生错误，请联系管理员！");
                            }
                        }
                    } catch (Exception e) {
                        MessageUtil.consoleError("在主线程中清理实体时发生错误！", e);
                        Player player = Bukkit.getPlayer(playerUuid);
                        if (player != null && player.isOnline()) {
                            player.sendMessage("§c删除岛屿时发生意外错误，请联系管理员！");
                        }
                    }
                }
            }.runTask(plugin);

        } catch (Exception e) {
            MessageUtil.consoleError("异步删除岛屿时发生错误！", e);
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
