package team.starm.starmskyblock.command.subcommand;

import org.bukkit.World;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Optional;

public class SetSpawnCommand extends SubCommand {

    public SetSpawnCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 1, "/is setspawn")) return true;

        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        var playerLocation = player.getLocation();
        World playerWorld = playerLocation.getWorld();
        var worldManager = plugin.getWorldManager();

        if (!worldManager.isSkyblockWorld(playerWorld)) {
            MessageUtil.sendMessage(player, "&c你只能在空岛维度设置传送点！");
            return true;
        }

        if (!plugin.getIslandManager().isPlayerOnIsland(player, island)) {
            MessageUtil.sendMessage(player, "&c你只能在你的岛屿范围内设置传送点！");
            return true;
        }

        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.SET_SPAWN)) {
            MessageUtil.sendMessage(player, "&c你没有权限设置传送点！");
            return true;
        }

        org.bukkit.Location blockBelow = playerLocation.clone().subtract(0, 1, 0);
        if (blockBelow.getBlock().getType().isAir()) {
            MessageUtil.sendMessage(player, "&c脚下不能是空气！请站在实心方块上设置传送点。");
            return true;
        }

        Island.WorldType worldType = playerWorld.equals(worldManager.getSkyblockNether())
                ? Island.WorldType.NETHER
                : playerWorld.equals(worldManager.getSkyblockEnd())
                  ? Island.WorldType.END
                  : Island.WorldType.NORMAL;

        var configManager = plugin.getConfigManager();
        if (worldType == Island.WorldType.NETHER && !configManager.isAllowSetspawnInNether()) {
            MessageUtil.sendMessage(player, "&c暂不支持在下界设置传送点！");
            return true;
        }
        if (worldType == Island.WorldType.END && !configManager.isAllowSetspawnInEnd()) {
            MessageUtil.sendMessage(player, "&c暂不支持在末地设置传送点！");
            return true;
        }

        if (plugin.getIslandManager().updateIslandCustomHome(island.getId(), worldType,
                playerLocation.getX(), playerLocation.getY(), playerLocation.getZ(),
                playerLocation.getYaw(), playerLocation.getPitch())) {
            MessageUtil.sendMessage(player, "&a已成功设置传送点！");
        } else {
            MessageUtil.sendMessage(player, "&c设置失败，请稍后重试。");
        }
        return true;
    }
}
