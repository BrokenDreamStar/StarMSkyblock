package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Arrays;
import java.util.Optional;

public class RenameCommand extends SubCommand {

    public RenameCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
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
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.RENAME_ISLAND)) {
            MessageUtil.sendMessage(player, "&c你没有权限修改岛屿名称！");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&c用法: /is rename <新名称>");
            return true;
        }

        String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (newName.length() > 32) {
            MessageUtil.sendMessage(player, "&c岛屿名称不能超过32个字符！");
            return true;
        }

        if (plugin.getIslandManager().updateIslandName(island.getId(), newName)) {
            MessageUtil.sendMessage(player, "&a岛屿名称已修改为: &e" + newName);
        } else {
            MessageUtil.sendMessage(player, "&c修改失败，请稍后重试。");
        }
        return true;
    }
}
