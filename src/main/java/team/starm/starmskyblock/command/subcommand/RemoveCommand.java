package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Optional;
import java.util.UUID;

public class RemoveCommand extends SubCommand {

    public RemoveCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.REMOVE_MEMBER)) {
            MessageUtil.sendMessage(player, "&c你没有权限踢出成员！");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&c用法: /is remove <玩家名> [confirm]");
            return true;
        }

        UUID targetUuid = island.getMembers().keySet().stream()
                .filter(uuid -> {
                    String name = getPlayerName(uuid);
                    return name != null && name.equalsIgnoreCase(args[1]);
                })
                .findFirst().orElse(null);
        if (targetUuid == null) {
            MessageUtil.sendMessage(player, "&c该玩家不是岛屿成员！");
            return true;
        }

        String targetName = getPlayerName(targetUuid);

        if (targetUuid.equals(island.getOwnerId())) {
            MessageUtil.sendMessage(player, "&c你不能踢出岛主！");
            return true;
        }

        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            MessageUtil.sendMessage(player, "&c警告：将踢出 &e" + targetName + " &c，使用 &e/is remove " + targetName + " confirm &c确认。");
            return true;
        }

        if (plugin.getIslandManager().removeMemberFromIsland(island.getId(), targetUuid)) {
            MessageUtil.sendMessage(player, "&a成功踢出 &e" + targetName + " &a从岛屿");
            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null) {
                MessageUtil.sendMessage(targetPlayer, "&c你已被 &e" + player.getName() + " &c从岛屿踢出");
            }
        } else {
            MessageUtil.sendMessage(player, "&c踢出失败，该玩家可能不是岛屿成员。");
        }
        return true;
    }
}
