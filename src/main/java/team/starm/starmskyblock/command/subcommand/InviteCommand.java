package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.InvitationManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Optional;

public class InviteCommand extends SubCommand {

    public InviteCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
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
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.INVITE_MEMBER)) {
            MessageUtil.sendMessage(player, "&c你没有权限邀请成员！");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&c用法: /is invite <玩家名>");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[1]);
        if (targetPlayer == null) {
            MessageUtil.sendMessage(player, "&c玩家不存在或不在线！");
            return true;
        }

        if (targetPlayer.getUniqueId().equals(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&c你不能邀请自己！");
            return true;
        }

        InvitationManager invitationManager = plugin.getInvitationManager();
        if (invitationManager.sendInvitation(player.getUniqueId(), targetPlayer.getUniqueId(), island.getId())) {
            MessageUtil.sendMessage(player, "&7等待对方确认...");
        } else {
            MessageUtil.sendMessage(player, "&c邀请失败！该玩家可能已有岛屿或已有待处理的邀请。");
        }
        return true;
    }
}
