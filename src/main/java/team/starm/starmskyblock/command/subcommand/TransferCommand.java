package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /is transfer} 子命令 —— 转让岛屿所有权。
 * <p>
 * 岛主可将岛屿转让给任意现有成员。需二次确认（{@code /is transfer <玩家> confirm}），
 * 转让后旧岛主降为 MEMBER，新岛主获得完整所有权。
 */
public class TransferCommand extends SubCommand {

    public TransferCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 3, "/is transfer <玩家名> [confirm]")) return true;

        // 1. 获取岛屿并校验岛主身份
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();
        if (!island.getOwnerId().equals(player.getUniqueId())) {
            MessageUtil.send(player, "transfer.owner-only");
            return true;
        }

        // 2. 需要目标玩家名
        if (args.length < 2) {
            MessageUtil.send(player, "transfer.usage");
            return true;
        }

        // 3. 查找目标玩家（从岛屿成员中按名称匹配，不区分大小写）
        UUID targetUuid = island.getMembers().keySet().stream()
                .filter(uuid -> {
                    String name = getPlayerName(uuid);
                    return name != null && name.equalsIgnoreCase(args[1]);
                })
                .findFirst().orElse(null);

        if (targetUuid == null) {
            MessageUtil.send(player, "transfer.not-member");
            return true;
        }

        // 4. 不能转让给自己
        if (targetUuid.equals(player.getUniqueId())) {
            MessageUtil.send(player, "transfer.self");
            return true;
        }

        String targetName = getPlayerName(targetUuid);

        // 5. 确认机制
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            MessageUtil.send(player, "transfer.confirm", Map.of("name", targetName));
            return true;
        }

        // 6. 执行转让
        plugin.getIslandManager().transferIsland(island.getId(), targetUuid);

        // 7. 通知双方
        String islandName = island.getName().isEmpty() ? String.valueOf(island.getId()) : island.getName();
        MessageUtil.send(player, "transfer.success", Map.of("name", targetName));
        Player targetPlayer = Bukkit.getPlayer(targetUuid);
        if (targetPlayer != null) {
            MessageUtil.send(targetPlayer, "transfer.new-owner", Map.of("island", islandName));
        }

        return true;
    }

    /**
     * Tab 补全：补全当前岛屿成员中权限等级低于岛主的玩家名（用于转让目标选择）
     */
    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        if (args.length != 2) return List.of();

        var islandOpt = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (islandOpt.isEmpty()) return List.of();
        Island island = islandOpt.get();

        // 只有岛主才能看到补全
        if (!island.getOwnerId().equals(player.getUniqueId())) return List.of();

        String prefix = args[1].toLowerCase();
        return island.getMembers().keySet().stream()
                .map(uuid -> {
                    var name = plugin.getPlayerRepo().getPlayerName(uuid);
                    return name.orElse(uuid.toString());
                })
                .filter(name -> name.toLowerCase().startsWith(prefix))
                .toList();
    }
}
