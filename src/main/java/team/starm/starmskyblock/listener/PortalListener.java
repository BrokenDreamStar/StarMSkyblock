package team.starm.starmskyblock.listener;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;

import java.util.Optional;

public class PortalListener implements Listener {

    private final StarMSkyblock plugin;

    public PortalListener(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        Location from = event.getFrom();
        World fromWorld = from.getWorld();
        if (fromWorld == null)
            return;

        Player player = event.getPlayer();
        IslandManager islandManager = plugin.getIslandManager();
        Optional<Island> optionalIsland = islandManager.getIsland(player.getUniqueId());

        // 获取配置文件中定义的世界名称
        ConfigManager config = plugin.getConfigManager();
        String normalName = config.getWorldNameNormal();
        String netherName = config.getWorldNameNether();
        String endName = config.getWorldNameEnd();

        TeleportCause cause = event.getCause();

        // ==================== 下界传送门已由 PlayerNetherListener 接管 ====================
        if (cause == TeleportCause.NETHER_PORTAL) {
            return;
        }

        // ==================== 末地传送门（END_PORTAL）处理 ====================
        if (cause == TeleportCause.END_PORTAL) {

            World targetWorld = null;
            Location targetLoc;

            // 情况1：从主世界 → 末地
            if (fromWorld.getName().equals(normalName)) {
                targetWorld = plugin.getWorldManager().getOrCreateSkyblockEnd();
            }
            // 情况2：从下界 → 末地
            else if (fromWorld.getName().equals(netherName)) {
                targetWorld = plugin.getWorldManager().getOrCreateSkyblockEnd();
            }
            // 情况3：从末地 → 主世界
            else if (fromWorld.getName().equals(endName)) {
                targetWorld = plugin.getWorldManager().getOrCreateSkyblockWorld();
            }

            if (targetWorld != null) {
                if (optionalIsland.isPresent()) {
                    // 玩家有岛屿 → 使用岛屿中心 + 该岛屿类型对应的偏移量
                    targetLoc = getIslandLocation(optionalIsland.get(), targetWorld);
                } else {
                    // 玩家没有岛屿 → 使用安全默认位置
                    if (fromWorld.getName().equals(endName)) {
                        // 从末地返回主世界 → 使用世界出生点
                        targetLoc = targetWorld.getSpawnLocation();
                    } else {
                        // 前往末地 → 使用固定安全坐标
                        targetLoc = new Location(targetWorld, 100, 50, 0);
                    }
                }
                event.setTo(targetLoc);
            }
        }
    }

    /**
     * 根据岛屿中心坐标 + 配置的偏移量生成传送目标位置
     *
     * 注意：传送门传送时始终使用“岛屿中心 + 结构偏移量”，不读取玩家自定义的 /is sethome 位置。
     * 只有 /is home 命令才会使用玩家自行设置的传送点。
     *
     * @param island 玩家岛屿实例
     * @param world  目标世界
     * @return 最终传送坐标
     */
    private Location getIslandLocation(Island island, World world) {
        ConfigManager config = plugin.getConfigManager();

        // 岛屿中心区块坐标 → 实际方块坐标（区块中心）
        int startX = island.getCenterChunkX() * 16;
        int startZ = island.getCenterChunkZ() * 16;
        int islandHeight = config.getIslandHeight();

        // 获取当前目标世界对应的偏移量
        double[] offsets = getTeleportOffsetsByWorldType(config, world, island);

        // 计算最终传送坐标：中心 + 8（区块正中心） + 配置偏移量
        double teleportX = startX + 8 + offsets[0];
        double teleportY = islandHeight + offsets[1];
        double teleportZ = startZ + 8 + offsets[2];

        return new Location(world, teleportX, teleportY, teleportZ);
    }

    /**
     * 根据目标世界类型获取对应结构的传送偏移量
     *
     * @param config 配置管理器
     * @param world  目标世界
     * @param island 岛屿实例（用于获取具体结构ID）
     * @return double[3] = {x偏移, y偏移, z偏移}
     */
    private double[] getTeleportOffsetsByWorldType(ConfigManager config, World world, Island island) {
        String worldName = world.getName();

        // 获取下界和末地世界的名称（用于判断世界类型）
        String netherName = plugin.getWorldManager().getSkyblockNether().getName();
        String endName = plugin.getWorldManager().getSkyblockEnd().getName();

        Island.WorldType worldType;
        if (worldName.equals(netherName)) {
            worldType = Island.WorldType.NETHER;
        } else if (worldName.equals(endName)) {
            worldType = Island.WorldType.END;
        } else {
            worldType = Island.WorldType.NORMAL;
        }

        // island 为 null 则使用默认结构
        String schematicId = (island != null) ? island.getSchematicId() : null;
        return config.getTeleportOffsetsBySchematicAndWorldType(schematicId, worldType);
    }
}
