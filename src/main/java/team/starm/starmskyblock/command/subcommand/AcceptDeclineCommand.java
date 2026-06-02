package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.message.MessageUtil;

public class AcceptDeclineCommand extends SubCommand {

    public AcceptDeclineCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 1, "/is " + args[0])) return true;

        var invitationManager = plugin.getInvitationManager();

        if (args[0].equalsIgnoreCase("accept")) {
            if (!invitationManager.hasPendingInvitation(player.getUniqueId())) {
                MessageUtil.sendMessage(player, "&c你没有待处理的岛屿邀请！");
                return true;
            }

            if (invitationManager.acceptInvitation(player.getUniqueId())) {
                MessageUtil.sendMessage(player, "&a你已成功加入岛屿！");
            } else {
                MessageUtil.sendMessage(player, "&c接受邀请失败，邀请可能已过期或你已有岛屿。");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("decline")) {
            if (!invitationManager.hasPendingInvitation(player.getUniqueId())) {
                MessageUtil.sendMessage(player, "&c你没有待处理的岛屿邀请！");
                return true;
            }

            if (invitationManager.declineInvitation(player.getUniqueId())) {
                MessageUtil.sendMessage(player, "&c你已拒绝岛屿邀请");
            } else {
                MessageUtil.sendMessage(player, "&c拒绝邀请失败，请稍后重试。");
            }
            return true;
        }

        return false;
    }
}
