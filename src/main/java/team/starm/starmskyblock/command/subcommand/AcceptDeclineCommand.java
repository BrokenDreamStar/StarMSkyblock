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
