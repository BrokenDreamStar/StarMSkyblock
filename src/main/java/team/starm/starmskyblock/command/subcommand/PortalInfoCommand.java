package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.Map;
import java.util.Optional;
import java.util.List;

/**
 * 传送门坐标查询命令（/is portalinfo）
 * <p>
 * 根据玩家当前所在维度（主世界/下界），复用 {@code PortalListener} 的传送门坐标换算公式，
 * 预计算对岸传送落点并告知玩家该落点是否落在自己岛屿的已解锁半径内。
 * 用于在搭传送门前确认对岸位置是否安全，避免传送到岛屿外或锁定区域。
 */
public class PortalInfoCommand extends SubCommand {

    public PortalInfoCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 执行 /is portalinfo 命令：解析当前维度并预览对岸传送落点归属状态。
     */
    @Override
    public boolean execute(Player player, String[] args) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return true;

        SkyblockWorldManager worldManager = plugin.getWorldManager();
        String worldName = world.getName();
        boolean fromNormal = worldManager.isNormalWorld(worldName);
        boolean fromNether = worldManager.isNetherWorld(worldName);
        boolean fromEnd = worldManager.isEndWorld(worldName);

        if (!fromNormal && !fromNether && !fromEnd) {
            MessageUtil.send(player, "island.portal.world-not-supported");
            return true;
        }

        if (fromEnd) {
            MessageUtil.send(player, "island.portal.end-dimension");
            return true;
        }

        IslandManager islandManager = plugin.getIslandManager();
        Optional<Island> islandOpt = islandManager.getIslandByPlayer(player.getUniqueId());
        if (islandOpt.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }
        Island island = islandOpt.get();

        // 复用 PortalListener 的中心偏移公式：主世界<->下界采用 8 倍缩放
        double centerX = island.getCenterChunkX() * 16.0 + 8.0;
        double centerZ = island.getCenterChunkZ() * 16.0 + 8.0;
        double offsetX = loc.getX() - centerX;
        double offsetZ = loc.getZ() - centerZ;

        double targetX, targetZ;
        String currentName, targetName;

        if (fromNormal) {
            targetX = centerX + offsetX / 8.0;
            targetZ = centerZ + offsetZ / 8.0;
            currentName = "主世界";
            targetName = "下界";
        } else {
            targetX = centerX + offsetX * 8.0;
            targetZ = centerZ + offsetZ * 8.0;
            currentName = "下界";
            targetName = "主世界";
        }

        int targetChunkX = (int) Math.floor(targetX) >> 4;
        int targetChunkZ = (int) Math.floor(targetZ) >> 4;
        boolean withinIsland = island.isChunkWithinIsland(targetChunkX, targetChunkZ);
        boolean withinMax = island.isChunkWithinMaxRange(targetChunkX, targetChunkZ);

        MessageUtil.send(player, "island.portal.current-position", Map.of(
                "world", currentName,
                "x", String.format("%.0f", loc.getX()),
                "y", String.format("%.0f", loc.getY()),
                "z", String.format("%.0f", loc.getZ())
        ));
        MessageUtil.send(player, "island.portal.target-position", Map.of(
                "world", targetName,
                "x", String.format("%.0f", targetX),
                "y", String.format("%.0f", loc.getY()),
                "z", String.format("%.0f", targetZ)
        ));

        if (withinIsland) {
            MessageUtil.send(player, "island.portal.within-island-yes");
        } else if (withinMax) {
            MessageUtil.send(player, "island.portal.within-island-locked");
        } else {
            MessageUtil.send(player, "island.portal.within-island-out");
        }

        return true;
    }

    /** 该命令无参数，不提供补全。 */
    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        return List.of();
    }
}