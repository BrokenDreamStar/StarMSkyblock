package team.starm.starmskyblock.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import team.starm.starmskyblock.StarMSkyblock;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class BorderListener implements Listener {

    private final StarMSkyblock plugin;
    private final IslandManager islandManager;

    // 缓存玩家当前所看到的岛屿标识，避免重复发送边界数据包
    private final Map<UUID, String> lastIslandCache = new HashMap<>();

    public BorderListener(StarMSkyblock plugin, IslandManager islandManager) {
        this.plugin = plugin;
        this.islandManager = islandManager;
    }

    // 统一处理玩家边界更新的逻辑
    private void updatePlayerBorder(Player player, Location location) {
        UUID playerUuid = player.getUniqueId();
        World playerWorld = location.getWorld();

        List<World> skyblockWorlds = Arrays.asList(
                plugin.getWorldManager().getSkyblockWorld(),
                plugin.getWorldManager().getSkyblockNether(),
                plugin.getWorldManager().getSkyblockEnd()
        );

        boolean isInSkyblockWorld = skyblockWorlds.stream().anyMatch(w -> w != null && w.equals(playerWorld));

        if (!isInSkyblockWorld) {
            if (lastIslandCache.containsKey(playerUuid)) {
                player.setWorldBorder(null);
                lastIslandCache.remove(playerUuid);
            }
            return;
        }

        // 获取玩家当前所在位置的岛屿
        Optional<Island> currentIslandOpt = islandManager.getIslandAt(location);

        if (currentIslandOpt.isPresent()) {
            Island island = currentIslandOpt.get();
            String islandKey = island.getCenterChunkX() + "," + island.getCenterChunkZ();

            // 缓存比对：如果没有变化，直接返回
            if (islandKey.equals(lastIslandCache.get(playerUuid))) {
                return;
            }

            if (!island.isShowBorder()) {
                player.setWorldBorder(null);
                lastIslandCache.put(playerUuid, "none");
                return;
            }

            int radiusChunks = island.getRadius();
            double sideLength = (radiusChunks * 2 + 1) * 16.0;

            int centerChunkX = island.getCenterChunkX();
            int centerChunkZ = island.getCenterChunkZ();
            double centerX = centerChunkX * 16.0 + 8.0;
            double centerZ = centerChunkZ * 16.0 + 8.0;

            WorldBorder border = Bukkit.createWorldBorder();
            border.setCenter(centerX, centerZ);
            border.setSize(sideLength);
            border.setWarningDistance(0);

            player.setWorldBorder(border);
            lastIslandCache.put(playerUuid, islandKey);

        } else {
            // 玩家在空岛世界的无岛屿区域（如虚空）
            if (!"none".equals(lastIslandCache.get(playerUuid))) {
                player.setWorldBorder(null);
                lastIslandCache.put(playerUuid, "none");
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) return;

        // 【性能核心】只在玩家跨越区块（Chunk）时才进行岛屿检测，忽略同一区块内的移动或仅视角的转动
        if (from.getBlockX() >> 4 != to.getBlockX() >> 4 || from.getBlockZ() >> 4 != to.getBlockZ() >> 4) {
            updatePlayerBorder(event.getPlayer(), to);
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // 传送后立即更新边界
        if (event.getTo() != null) {
            updatePlayerBorder(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        // 切换世界后更新边界
        updatePlayerBorder(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 玩家上线时初始化边界
        updatePlayerBorder(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 玩家下线时清理缓存，防止内存泄漏
        lastIslandCache.remove(event.getPlayer().getUniqueId());
    }
}
