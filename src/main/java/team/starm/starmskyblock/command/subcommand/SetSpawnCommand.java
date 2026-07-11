package team.starm.starmskyblock.command.subcommand;

import org.bukkit.World;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Optional;
import org.bukkit.Location;
import team.starm.starmskyblock.StarMSkyblock;

/**
 * {@code /is setspawn} 子命令 -- 设置岛屿自定义出生点。
 * <p>
 * 将玩家当前位置记录为岛屿出生点，须站在所属岛屿的已解锁范围内、
 * 脚下有实心方块；下界/末地出生点受配置开关控制。
 */
public class SetSpawnCommand extends SubCommand {

    public SetSpawnCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 校验世界/岛屿归属/权限/脚下方块后写入自定义出生点。
     */
    @Override
    public boolean execute(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 1, "/is setspawn")) return true;

        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();
        var playerLocation = player.getLocation();
        World playerWorld = playerLocation.getWorld();
        var worldManager = plugin.getWorldManager();

        if (!worldManager.isSkyblockWorld(playerWorld)) {
            MessageUtil.send(player, "spawn.set.skyblock-dimension-only");
            return true;
        }

        if (!plugin.getIslandManager().isPlayerOnIsland(player, island)) {
            MessageUtil.send(player, "spawn.set.within-island-only");
            return true;
        }

        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.SET_SPAWN)) {
            MessageUtil.send(player, "spawn.set.no-permission");
            return true;
        }

        Location blockBelow = playerLocation.clone().subtract(0, 1, 0);
        if (blockBelow.getBlock().getType().isAir()) {
            MessageUtil.send(player, "spawn.set.no-block-below");
            return true;
        }

        Island.WorldType worldType = playerWorld.equals(worldManager.getSkyblockNether())
                ? Island.WorldType.NETHER
                : playerWorld.equals(worldManager.getSkyblockEnd())
                  ? Island.WorldType.END
                  : Island.WorldType.NORMAL;

        var configManager = plugin.getConfigManager();
        if (worldType == Island.WorldType.NETHER && !configManager.isAllowSetspawnInNether()) {
            MessageUtil.send(player, "spawn.set.nether-not-supported");
            return true;
        }
        if (worldType == Island.WorldType.END && !configManager.isAllowSetspawnInEnd()) {
            MessageUtil.send(player, "spawn.set.end-not-supported");
            return true;
        }

        if (plugin.getIslandManager().updateIslandCustomHome(island.getId(), worldType,
                playerLocation.getX(), playerLocation.getY(), playerLocation.getZ(),
                playerLocation.getYaw(), playerLocation.getPitch())) {
            MessageUtil.send(player, "spawn.set.success");
        } else {
            MessageUtil.send(player, "general.operation-failed");
        }
        return true;
    }
}
