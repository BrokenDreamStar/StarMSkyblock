package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.StarMSkyblock;

/**
 * {@code /is accept|decline} 子命令 -- 接受或拒绝岛屿邀请。
 * <p>
 * 从邀请管理器读取待处理邀请，执行对应操作后向玩家反馈结果。
 */
public class AcceptDeclineCommand extends SubCommand {

    public AcceptDeclineCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 处理 accept/decline：无待处理邀请时提示，否则执行并反馈成功/失败。
     */
    @Override
    public boolean execute(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 1, "/is " + args[0])) return true;

        var invitationManager = plugin.getInvitationManager();

        if (args[0].equalsIgnoreCase("accept")) {
            if (!invitationManager.hasPendingInvitation(player.getUniqueId())) {
                MessageUtil.send(player, "team.invite.no-pending");
                return true;
            }

            if (invitationManager.acceptInvitation(player.getUniqueId())) {
                MessageUtil.send(player, "team.accept.success");
            } else {
                MessageUtil.send(player, "team.accept.failed");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("decline")) {
            if (!invitationManager.hasPendingInvitation(player.getUniqueId())) {
                MessageUtil.send(player, "team.invite.no-pending");
                return true;
            }

            if (invitationManager.declineInvitation(player.getUniqueId())) {
                MessageUtil.send(player, "team.decline.success");
            } else {
                MessageUtil.send(player, "team.decline.failed");
            }
            return true;
        }

        return false;
    }
}
