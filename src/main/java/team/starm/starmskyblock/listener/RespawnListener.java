package team.starm.starmskyblock.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.Optional;

/**
 * 玩家重生监听器 —— 将玩家的重生点设为岛屿传送点而非世界出生点。
 * <p>
 * 当配置项 {@code set-respawn-on-join} 启用时，
 * 玩家在岛屿上死亡后将在岛屿的传送坐标处复活，
 * 从而避免出现"你的床或已充能的重生锚不存在"的提示。
 */
public class

RespawnListener implements Listener {

    private final IslandManager islandManager;
    private final ConfigManager configManager;
    private final SkyblockWorldManager worldManager;

    public RespawnListener(IslandManager islandManager, ConfigManager configManager, SkyblockWorldManager worldManager) {
        this.islandManager = islandManager;
        this.configManager = configManager;
        this.worldManager = worldManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!configManager.isSetRespawnOnJoin()) {
            return;
        }

        // 玩家有有效的床或重生锚重生点时，不干扰其正常重生
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }

        Player player = event.getPlayer();
        Optional<Island> islandOpt = islandManager.getIslandByPlayer(player.getUniqueId());

        if (islandOpt.isPresent()) {
            Island island = islandOpt.get();

            // 优先使用玩家自定义的岛屿传送点（/is setspawn）
            if (island.hasCustomHome()) {
                World homeWorld = switch (island.getCustomHomeWorldType()) {
                    case NETHER -> worldManager.getSkyblockNether();
                    case END -> worldManager.getSkyblockEnd();
                    default -> worldManager.getSkyblockWorld();
                };
                if (homeWorld != null) {
                    event.setRespawnLocation(new Location(
                            homeWorld,
                            island.getCustomHomeX(), island.getCustomHomeY(), island.getCustomHomeZ(),
                            island.getCustomHomeYaw(), island.getCustomHomePitch()
                    ));
                    return;
                }
            }

            // 未设置自定义传送点时，使用岛屿默认出生点
            World normalWorld = worldManager.getSkyblockWorld();
            if (normalWorld != null) {
                double[] offsets = configManager.getTeleportOffsetsBySchematicAndWorldType(
                        island.getSchematicId(), Island.WorldType.NORMAL);
                double teleportX = (island.getCenterChunkX() * 16) + 8 + offsets[0];
                double teleportY = configManager.getIslandHeight() + offsets[1];
                double teleportZ = (island.getCenterChunkZ() * 16) + 8 + offsets[2];

                event.setRespawnLocation(new Location(normalWorld, teleportX, teleportY, teleportZ,
                        (float) offsets[3], (float) offsets[4]));
                return;
            }
        }

        // 岛屿传送点不可用且无床/重生锚时，使用配置的 fallback-spawn
        if (configManager.hasFallbackSpawn()) {
            World fallbackWorld = Bukkit.getWorld(configManager.getFallbackSpawnWorld());
            if (fallbackWorld != null) {
                event.setRespawnLocation(new Location(
                        fallbackWorld,
                        configManager.getFallbackSpawnX(),
                        configManager.getFallbackSpawnY(),
                        configManager.getFallbackSpawnZ(),
                        configManager.getFallbackSpawnYaw(),
                        configManager.getFallbackSpawnPitch()
                ));
            }
        }
    }
}
