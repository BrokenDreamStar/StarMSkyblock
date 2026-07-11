package team.starm.starmskyblock.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 岛屿删除异步任务 -- 清空三个世界的方块 -> 清理实体 -> 删除数据库记录。
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

    /** 异步执行方块清空 -> 调度主线程清理实体和数据库 */
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

            // FAWE 引擎可在异步线程安全修改区块；标准 WorldEdit 必须在主线程执行，
            // 否则会从异步线程直接改 chunk -> 区块损坏。
            boolean faweActive = plugin.getSchematicManager().isFaweActive();

            Runnable clearTask = () -> {
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
            };

            Runnable finalizeTask = () -> finalizeDeletion(world, netherWorld, endWorld, minX, maxX, minZ, maxZ);

            if (faweActive) {
                // FAWE：异步线程清空方块 -> 主线程清理实体和数据库
                clearTask.run();
                new BukkitRunnable() {
                    @Override
                    public void run() { finalizeTask.run(); }
                }.runTask(plugin);
            } else {
                // 非 FAWE：clearArea 必须在主线程执行，clearArea + finalize 一起调度
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            clearTask.run();
                            finalizeTask.run();
                        } catch (Exception e) {
                            MessageUtil.consoleError("在主线程中执行岛屿删除时发生错误！", e);
                            notifyError();
                        }
                    }
                }.runTask(plugin);
            }

        } catch (Exception e) {
            MessageUtil.consoleError("异步删除岛屿时发生错误！", e);
            new BukkitRunnable() {
                @Override
                public void run() {
                    Player player = Bukkit.getPlayer(playerUuid);
                    if (player != null && player.isOnline()) {
                        MessageUtil.send(player, "island.delete.task-error");
                    }
                }
            }.runTask(plugin);
        }
    }

    /** 主线程阶段：传送玩家、清理实体、删除数据库记录、通知玩家 */
    private void finalizeDeletion(World world, World netherWorld, World endWorld,
                                  int minX, int maxX, int minZ, int maxZ) {
        try {
            Location spawnLoc = world != null ? world.getSpawnLocation() : null;

            // 玩家检测：用岛屿 chunk 集合做 O(1) 包含判定，替代对每个在线玩家
            // 调用 BoundingBox.contains(Vector)。高度信息对"是否站在岛上"无意义
            // （玩家在岛屿 XZ 范围内的任意高度都应被踢出）。
            Set<Long> islandChunks = new HashSet<>();
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                    islandChunks.add(GridKeys.encode(cx, cz));
                }
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                World pWorld = p.getWorld();
                boolean inSkyblockWorld = pWorld != null &&
                        (pWorld.equals(world) || pWorld.equals(netherWorld) || pWorld.equals(endWorld));
                if (!inSkyblockWorld) continue;
                long chunkKey = GridKeys.encode(p.getLocation().getBlockX() >> 4, p.getLocation().getBlockZ() >> 4);
                if (islandChunks.contains(chunkKey)) {
                    if (spawnLoc != null) p.teleport(spawnLoc);
                    MessageUtil.send(p, "island.delete.task-ejected");
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
                    MessageUtil.send(player, "island.delete.task-success",
                            Map.of("current", deleteCount + 1, "max", maxDeleteTimes));
                }

                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (onlinePlayer.hasPermission("skyblock.admin") && !onlinePlayer.getUniqueId().equals(playerUuid)) {
                        String displayName = (player != null) ? player.getName() : playerUuid.toString();
                        MessageUtil.send(onlinePlayer, "island.delete.admin-broadcast", Map.of("player", displayName));
                    }
                }
            } else {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    MessageUtil.send(player, "island.delete.task-data-error");
                }
            }
        } catch (Exception e) {
            MessageUtil.consoleError("在主线程中清理实体时发生错误！", e);
            notifyError();
        }
    }

    private void notifyError() {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            MessageUtil.send(player, "island.delete.task-error");
        }
    }
}
