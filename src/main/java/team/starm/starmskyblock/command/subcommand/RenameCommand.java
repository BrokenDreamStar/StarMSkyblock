package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import team.starm.starmskyblock.StarMSkyblock;

/**
 * {@code /is rename} 子命令 -- 重命名所属岛屿。
 * <p>
 * 受重命名权限和冷却时间限制，名称长度不得超过配置上限。
 */
public class RenameCommand extends SubCommand {

    /** 每个玩家上次重命名的时间戳，用于冷却控制。 */
    private final Map<UUID, Long> renameCooldowns = new ConcurrentHashMap<>();

    public RenameCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 校验权限与冷却后重命名岛屿。参数中空格拼接为完整名称。
     */
    @Override
    public boolean execute(Player player, String[] args) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.RENAME_ISLAND)) {
            MessageUtil.send(player, "island.rename.no-permission");
            return true;
        }

        int cooldown = plugin.getConfigManager().getRenameCooldown();
        if (cooldown > 0) {
            Long lastRename = renameCooldowns.get(player.getUniqueId());
            if (lastRename != null) {
                long cooldownMs = cooldown * 1000L;
                long elapsed = System.currentTimeMillis() - lastRename;
                if (elapsed < cooldownMs) {
                    long remaining = cooldown - (elapsed / 1000);
                    MessageUtil.send(player, "island.rename.cooldown", Map.of("remaining", remaining));
                    return true;
                }
            }
        }

        if (args.length < 2) {
            MessageUtil.send(player, "island.rename.usage");
            return true;
        }

        String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        int maxLength = plugin.getConfigManager().getMaxNameLength();
        if (MessageUtil.stripColor(newName).length() > maxLength) {
            MessageUtil.send(player, "island.rename.too-long", Map.of("max", maxLength));
            return true;
        }

        if (plugin.getIslandManager().updateIslandName(island.getId(), newName)) {
            renameCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            MessageUtil.send(player, "island.rename.success", Map.of("name", newName));
        } else {
            MessageUtil.send(player, "general.operation-failed");
        }
        return true;
    }
}
