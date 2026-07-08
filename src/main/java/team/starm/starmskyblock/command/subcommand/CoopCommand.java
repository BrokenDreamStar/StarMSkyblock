package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class CoopCommand extends SubCommand {

    public CoopCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(player, "coop.usage.header");
            MessageUtil.send(player, "coop.usage.add");
            MessageUtil.send(player, "coop.usage.remove");
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "add" -> handleAdd(player, args);
            case "remove" -> handleRemove(player, args);
            default -> {
                MessageUtil.send(player, "coop.unknown-subcommand", Map.of("subcommand", args[1]));
                MessageUtil.send(player, "coop.usage.summary");
                yield true;
            }
        };
    }

    private boolean handleAdd(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 3, "/is coop add <玩家名>")) return true;

        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.INVITE_COOP)) {
            MessageUtil.send(player, "coop.add.no-permission");
            return true;
        }

        if (args.length < 3) {
            MessageUtil.send(player, "coop.add.usage");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[2]);
        if (targetPlayer == null) {
            MessageUtil.send(player, "general.player-not-found");
            return true;
        }

        if (targetPlayer.getUniqueId().equals(player.getUniqueId())) {
            MessageUtil.send(player, "coop.add.self");
            return true;
        }

        var islandManager = plugin.getIslandManager();
        if (islandManager.getIslandByPlayer(targetPlayer.getUniqueId()).isEmpty()) {
            MessageUtil.send(player, "coop.add.target-no-island");
            return true;
        }

        if (island.isCoop(targetPlayer.getUniqueId())) {
            MessageUtil.send(player, "coop.add.already-coop");
            return true;
        }

        if (island.getMembers().containsKey(targetPlayer.getUniqueId())) {
            MessageUtil.send(player, "coop.add.already-member");
            return true;
        }

        if (islandManager.addCoopToIsland(island.getId(), targetPlayer.getUniqueId())) {
            MessageUtil.send(player, "coop.add.success", Map.of("name", targetPlayer.getName()));
            MessageUtil.send(targetPlayer, "coop.add.added", Map.of("player", player.getName()));
        } else {
            MessageUtil.send(player, "general.operation-failed");
        }
        return true;
    }

    private boolean handleRemove(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 3, "/is coop remove <玩家名>")) return true;

        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.REMOVE_COOP)) {
            MessageUtil.send(player, "coop.remove.no-permission");
            return true;
        }

        if (args.length < 3) {
            MessageUtil.send(player, "coop.remove.usage");
            return true;
        }

        UUID targetUuid = island.getCoops().stream()
                .filter(uuid -> {
                    String name = getPlayerName(uuid);
                    return name != null && name.equalsIgnoreCase(args[2]);
                })
                .findFirst().orElse(null);
        if (targetUuid == null) {
            MessageUtil.send(player, "coop.remove.not-coop");
            return true;
        }

        String targetName = getPlayerName(targetUuid);

        if (targetUuid.equals(island.getOwnerId())) {
            MessageUtil.send(player, "general.cannot-remove-owner");
            return true;
        }

        if (plugin.getIslandManager().removeCoopFromIsland(island.getId(), targetUuid)) {
            MessageUtil.send(player, "coop.remove.success", Map.of("name", targetName));
            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null) {
                MessageUtil.send(targetPlayer, "coop.remove.removed", Map.of("player", player.getName()));
            }
        } else {
            MessageUtil.send(player, "general.operation-failed");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return List.of("add", "remove").stream()
                    .filter(v -> v.startsWith(prefix))
                    .toList();
        }
        if (args.length == 3) {
            String sub = args[1].toLowerCase();
            var islandOpt = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
            if (islandOpt.isEmpty()) return List.of();
            Island island = islandOpt.get();
            String prefix = args[2].toLowerCase();
            if (sub.equals("add")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> {
                            Player p = Bukkit.getPlayer(name);
                            if (p == null || p.getUniqueId().equals(player.getUniqueId())) return false;
                            if (island.isCoop(p.getUniqueId())) return false;
                            return plugin.getIslandManager().getIslandByPlayer(p.getUniqueId()).isPresent();
                        })
                        .filter(name -> name.toLowerCase().startsWith(prefix))
                        .toList();
            }
            if (sub.equals("remove")) {
                return island.getCoops().stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(prefix))
                        .toList();
            }
        }
        return List.of();
    }
}
