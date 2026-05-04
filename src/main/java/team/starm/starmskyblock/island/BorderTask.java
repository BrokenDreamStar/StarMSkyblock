package team.starm.starmskyblock.island;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import team.starm.starmskyblock.StarMSkyblock;

import java.util.Arrays;
import java.util.List;

public class BorderTask extends BukkitRunnable {

    private final StarMSkyblock plugin;
    private final IslandManager islandManager;

    public BorderTask(StarMSkyblock plugin, IslandManager islandManager) {
        this.plugin = plugin;
        this.islandManager = islandManager;
    }

    @Override
    public void run() {
        List<World> skyblockWorlds = Arrays.asList(
            plugin.getWorldManager().getSkyblockWorld(),
            plugin.getWorldManager().getSkyblockNether(),
            plugin.getWorldManager().getSkyblockEnd()
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            World playerWorld = player.getWorld();
            boolean isInSkyblockWorld = skyblockWorlds.stream().anyMatch(w -> w != null && w.equals(playerWorld));

            if (!isInSkyblockWorld) {
                // 如果玩家不在空岛世界，清除个人边界，使用世界默认边界
                player.setWorldBorder(null);
                continue;
            }

            // 获取玩家所在岛屿，显示玩家自己的岛屿边界
            islandManager.getIsland(player.getUniqueId()).ifPresentOrElse(island -> {
                if (!island.isShowBorder()) {
                    player.setWorldBorder(null);
                    return;
                }

                // 半径单位为区块。中心区块包含在内，因此总宽度为 (radius * 2 + 1) 个区块，每个区块 16 格。
                int radiusChunks = island.getRadius();
                double sideLength = (radiusChunks * 2 + 1) * 16.0;

                // 中心点坐标
                int centerChunkX = island.getCenterChunkX();
                int centerChunkZ = island.getCenterChunkZ();
                double centerX = centerChunkX * 16.0 + 8.0;
                double centerZ = centerChunkZ * 16.0 + 8.0;

                // 创建并设置玩家专用的 WorldBorder
                WorldBorder border = Bukkit.createWorldBorder();
                border.setCenter(centerX, centerZ);
                border.setSize(sideLength);

                // 为了只显示视觉效果而不会立刻造成伤害，可以设置较大的警告距离
                border.setWarningDistance(0);

                player.setWorldBorder(border);

            }, () -> {
                // 如果玩家没有岛屿，则清除边界
                player.setWorldBorder(null);
            });
        }
    }
}
