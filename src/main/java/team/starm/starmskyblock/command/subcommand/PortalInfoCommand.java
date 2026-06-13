package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.Optional;
import java.util.List;

public class PortalInfoCommand extends SubCommand {

    public PortalInfoCommand(StarMSkyblock plugin) {
        super(plugin);
    }

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
            MessageUtil.sendMessage(player, "§c当前世界不支持下界传送门。");
            return true;
        }

        if (fromEnd) {
            MessageUtil.sendMessage(player, "§c当前处于末地维度，无法传送至下界/主世界传送门。");
            return true;
        }

        IslandManager islandManager = plugin.getIslandManager();
        Optional<Island> islandOpt = islandManager.getIslandByPlayer(player.getUniqueId());
        if (islandOpt.isEmpty()) {
            MessageUtil.sendMessage(player, "§c你还没有岛屿！");
            return true;
        }
        Island island = islandOpt.get();

        // 复用 PortalListener 的中心偏移公式
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

        MessageUtil.sendMessage(player, String.format(
                "§b[岛屿] 当前位置：%s (%.0f, %.0f, %.0f)",
                currentName, loc.getX(), loc.getY(), loc.getZ()
        ));
        MessageUtil.sendMessage(player, String.format(
                "§b[岛屿] 下界传送门目标：%s (%.0f, %.0f, %.0f)",
                targetName, targetX, loc.getY(), targetZ
        ));

        if (withinIsland) {
            MessageUtil.sendMessage(player, "§a[岛屿] 是否在岛屿范围内：是 ✓");
        } else if (withinMax) {
            MessageUtil.sendMessage(player, "§c[岛屿] 是否在岛屿范围内：目标位置岛屿区域未解锁 ✗");
        } else {
            MessageUtil.sendMessage(player, "§c[岛屿] 是否在岛屿范围内：目标位置超出你的岛屿范围 ✗");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        return List.of();
    }
}