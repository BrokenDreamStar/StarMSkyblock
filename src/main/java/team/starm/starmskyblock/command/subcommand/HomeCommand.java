package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.Island.WorldType;
import team.starm.starmskyblock.listener.TeleportCountdownListener;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Optional;

public class HomeCommand extends SubCommand {

    public HomeCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！使用 /is create 创建。");
            return true;
        }

        Island island = optionalIsland.get();
        Location spawnLocation;
        World targetWorld;

        if (island.hasCustomHome()) {
            WorldType worldType = island.getCustomHomeWorldType();
            targetWorld = switch (worldType) {
                case NETHER -> plugin.getWorldManager().getSkyblockNether();
                case END -> plugin.getWorldManager().getSkyblockEnd();
                default -> plugin.getWorldManager().getSkyblockWorld();
            };
            spawnLocation = new Location(targetWorld,
                    island.getCustomHomeX(), island.getCustomHomeY(), island.getCustomHomeZ());

            if (!isLocationSafe(spawnLocation) && (args.length == 1 || !args[1].equalsIgnoreCase("confirm"))) {
                MessageUtil.sendMessage(player, "&c警告：传送点不安全！脚下可能是空气。");
                MessageUtil.sendMessage(player, "&c使用 &e/is home confirm &c强制传送（可能摔落）。");
                return true;
            }

            String worldName = worldType == WorldType.NORMAL ? "主世界"
                    : worldType == WorldType.NETHER ? "下界" : "末地";
            String successMessage = "&a已传送到你设置的岛屿 &e" + worldName + " &a传送点！";
            teleportWithCountdown(player, spawnLocation, successMessage);
        } else {
            targetWorld = plugin.getWorldManager().getSkyblockWorld();
            ConfigManager config = plugin.getConfigManager();
            double[] offsets = config.getTeleportOffsetsBySchematicAndWorldType(
                    island.getSchematicId(), WorldType.NORMAL);

            double teleportX = (island.getCenterChunkX() * 16) + 8 + offsets[0];
            double teleportY = config.getIslandHeight() + offsets[1];
            double teleportZ = (island.getCenterChunkZ() * 16) + 8 + offsets[2];

            spawnLocation = new Location(targetWorld, teleportX, teleportY, teleportZ);

            if (!isLocationSafe(spawnLocation) && (args.length == 1 || !args[1].equalsIgnoreCase("confirm"))) {
                MessageUtil.sendMessage(player, "&c警告：传送点不安全！脚下可能是空气。");
                MessageUtil.sendMessage(player, "&c使用 &e/is home confirm &c强制传送（可能摔落）。");
                return true;
            }

            teleportWithCountdown(player, spawnLocation, "&a欢迎回到你的岛屿！");
        }

        return true;
    }

    private void teleportWithCountdown(Player player, Location location, String successMessage) {
        int countdown = plugin.getConfigManager().getTeleportCountdown();
        if (countdown > 0) {
            TeleportCountdownListener.startCountdown(player, location, countdown, successMessage);
        } else {
            player.teleport(location);
            MessageUtil.sendMessage(player, successMessage);
        }
    }
}
