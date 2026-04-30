package team.starm.starmskyblock.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandCreateTask;
import team.starm.starmskyblock.island.IslandDeleteTask;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.island.InvitationManager;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 空岛主命令处理器 - IslandCommand
 * 处理所有 /is 子命令
 */
public class IslandCommand implements CommandExecutor, TabCompleter {

    private final StarMSkyblock plugin;
    private final IslandPermissionCommand permissionCommand;

    private final List<String> subCommands = Arrays.asList(
            "create", "home", "sethome", "border", "delete", "help",
            "invite", "kick", "promote", "demote", "members", "role",
            "accept", "decline", "permission", "permissions");

    public IslandCommand(StarMSkyblock plugin) {
        this.plugin = plugin;
        this.permissionCommand = new IslandPermissionCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, "&c只有玩家才能执行该命令！");
            return true;
        }

        if (!sender.hasPermission("skyblock.is")) {
            MessageUtil.sendMessage(sender, "&c你没有权限执行此命令！");
            return true;
        }

        IslandManager islandManager = plugin.getIslandManager();
        SkyblockWorldManager worldManager = plugin.getWorldManager();

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelpMessage(player);
            return true;
        }

        // ====================== 创建岛屿 ======================
        if (args[0].equalsIgnoreCase("create")) {
            if (islandManager.getIsland(player.getUniqueId()).isPresent()) {
                MessageUtil.sendMessage(player, "&c你已经拥有一个岛屿了！使用 /is home 返回。");
                return true;
            }

            String schematicId = plugin.getConfigManager().getDefaultNormalSchematicId();
            String islandName = null;

            if (args.length > 1) {
                String firstArg = args[1].toLowerCase();
                if (plugin.getConfigManager().getNormalSchematics().containsKey(firstArg)) {
                    schematicId = firstArg;
                    if (args.length > 2) {
                        islandName = args[2];
                    }
                } else {
                    islandName = args[1];
                }
            }

            if (islandName == null || islandName.isEmpty()) {
                islandName = player.getName() + "的岛屿";
            }

            String finalIslandName = islandName;
            MessageUtil.sendMessage(player, "&a================================");
            MessageUtil.sendMessage(player, "&a开始异步创建岛屿...");
            MessageUtil.sendMessage(player, "&a岛屿类型: &e" + schematicId);
            MessageUtil.sendMessage(player, "&a岛屿名称: &e" + finalIslandName);
            MessageUtil.sendMessage(player, "&a================================");

            IslandCreateTask createTask = new IslandCreateTask(plugin, islandManager, player.getUniqueId(),
                    schematicId, finalIslandName);
            createTask.runTaskAsynchronously(plugin);
            return true;
        }

        // ====================== 传送回家 ======================
        if (args[0].equalsIgnoreCase("home")) {
            Optional<Island> optionalIsland = islandManager.getIsland(player.getUniqueId());
            if (optionalIsland.isEmpty()) {
                MessageUtil.sendMessage(player, "&c你还没有岛屿！使用 /is create 创建。");
                return true;
            }

            Island island = optionalIsland.get();
            Location spawnLocation;
            World targetWorld;

            if (island.hasCustomHome()) {
                Island.WorldType worldType = island.getCustomHomeWorldType();
                switch (worldType) {
                    case NETHER -> targetWorld = worldManager.getSkyblockNether();
                    case END -> targetWorld = worldManager.getSkyblockEnd();
                    default -> targetWorld = worldManager.getSkyblockWorld();
                }
                spawnLocation = new Location(targetWorld,
                        island.getCustomHomeX(),
                        island.getCustomHomeY(),
                        island.getCustomHomeZ());

                if (!isLocationSafe(spawnLocation) && (args.length == 1 || !args[1].equalsIgnoreCase("confirm"))) {
                    MessageUtil.sendMessage(player, "&c警告：传送点不安全！脚下可能是空气。");
                    MessageUtil.sendMessage(player, "&c使用 &e/is home confirm &c强制传送（可能摔落）。");
                    return true;
                }

                String worldName = worldType == Island.WorldType.NORMAL ? "主世界"
                        : worldType == Island.WorldType.NETHER ? "下界" : "末地";
                MessageUtil.sendMessage(player, "&a已传送到你设置的岛屿 &e" + worldName + " &a传送点！");
            } else {
                targetWorld = worldManager.getSkyblockWorld();
                ConfigManager config = plugin.getConfigManager();
                double[] offsets = config.getTeleportOffsetsBySchematicAndWorldType(
                        island.getSchematicId(), Island.WorldType.NORMAL);

                double teleportX = (island.getCenterChunkX() * 16) + 8 + offsets[0];
                double teleportY = config.getIslandHeight() + offsets[1];
                double teleportZ = (island.getCenterChunkZ() * 16) + 8 + offsets[2];

                spawnLocation = new Location(targetWorld, teleportX, teleportY, teleportZ);

                if (!isLocationSafe(spawnLocation) && (args.length == 1 || !args[1].equalsIgnoreCase("confirm"))) {
                    MessageUtil.sendMessage(player, "&c警告：传送点不安全！脚下可能是空气。");
                    MessageUtil.sendMessage(player, "&c使用 &e/is home confirm &c强制传送（可能摔落）。");
                    return true;
                }

                MessageUtil.sendMessage(player, "&a欢迎回到你的岛屿！");
            }

            player.teleport(spawnLocation);
            return true;
        }

        // ====================== 切换岛屿边界显示 ======================
        if (args[0].equalsIgnoreCase("border")) {
            Optional<Island> optionalIsland = islandManager.getIsland(player.getUniqueId());
            if (optionalIsland.isEmpty()) {
                MessageUtil.sendMessage(player, "&c你还没有岛屿！");
                return true;
            }

            Island island = optionalIsland.get();
            boolean newBorderStatus = !island.isShowBorder();
            island.setShowBorder(newBorderStatus);

            if (newBorderStatus) {
                MessageUtil.sendMessage(player, "&a岛屿边界显示已开启！");
            } else {
                MessageUtil.sendMessage(player, "&c岛屿边界显示已关闭！");
            }
            return true;
        }

        // ====================== 设置自定义传送点 ======================
        if (args[0].equalsIgnoreCase("sethome")) {
            Optional<Island> optionalIsland = islandManager.getIsland(player.getUniqueId());
            if (optionalIsland.isEmpty()) {
                MessageUtil.sendMessage(player, "&c你还没有岛屿！");
                return true;
            }

            Island island = optionalIsland.get();
            Location playerLocation = player.getLocation();
            World playerWorld = playerLocation.getWorld();

            boolean isInSkyblockWorld = playerWorld.equals(worldManager.getSkyblockWorld())
                    || playerWorld.equals(worldManager.getSkyblockNether())
                    || playerWorld.equals(worldManager.getSkyblockEnd());

            if (!isInSkyblockWorld) {
                MessageUtil.sendMessage(player, "&c你只能在空岛维度设置传送点！");
                return true;
            }

            if (!islandManager.isPlayerOnOwnIsland(player)) {
                MessageUtil.sendMessage(player, "&c你只能在你的岛屿范围内设置传送点！");
                return true;
            }

            Location blockBelowLocation = playerLocation.clone().subtract(0, 1, 0);
            if (blockBelowLocation.getBlock().getType().isAir()) {
                MessageUtil.sendMessage(player, "&c脚下不能是空气！请站在实心方块上设置传送点。");
                return true;
            }

            Island.WorldType worldType = playerWorld.equals(worldManager.getSkyblockNether()) ? Island.WorldType.NETHER
                    : playerWorld.equals(worldManager.getSkyblockEnd()) ? Island.WorldType.END
                            : Island.WorldType.NORMAL;

            ConfigManager configManager = plugin.getConfigManager();
            if (worldType == Island.WorldType.NETHER && !configManager.isAllowSethomeInNether()) {
                MessageUtil.sendMessage(player, "&c当前配置不允许在下界设置传送点！");
                return true;
            }
            if (worldType == Island.WorldType.END && !configManager.isAllowSethomeInEnd()) {
                MessageUtil.sendMessage(player, "&c当前配置不允许在末地设置传送点！");
                return true;
            }

            if (islandManager.updateIslandCustomHome(island.getId(), worldType,
                    playerLocation.getX(), playerLocation.getY(), playerLocation.getZ())) {
                String worldName = worldType == Island.WorldType.NORMAL ? "主世界"
                        : worldType == Island.WorldType.NETHER ? "下界" : "末地";
                MessageUtil.sendMessage(player, "&a传送点已设置在 &e" + worldName + "&a！");
            } else {
                MessageUtil.sendMessage(player, "&c设置失败，请稍后重试。");
            }
            return true;
        }

        // ====================== 删除岛屿 ======================
        if (args[0].equalsIgnoreCase("delete")) {
            Optional<Island> optionalIsland = islandManager.getIsland(player.getUniqueId());
            if (optionalIsland.isEmpty()) {
                MessageUtil.sendMessage(player, "&c你还没有岛屿！");
                return true;
            }

            Island island = optionalIsland.get();
            if (!island.getOwnerId().equals(player.getUniqueId())) {
                MessageUtil.sendMessage(player, "&c只有岛主才能删除岛屿！");
                return true;
            }

            if (args.length == 1 || !args[1].equalsIgnoreCase("confirm")) {
                MessageUtil.sendMessage(player, "&c警告：这将永久删除你的岛屿！使用 &e/is delete confirm &c确认。");
                return true;
            }

            int deleteCount = islandManager.getDeleteCount(player.getUniqueId());
            int maxDeleteTimes = plugin.getConfigManager().getMaxDeleteTimes();
            if (deleteCount >= maxDeleteTimes) {
                MessageUtil.sendMessage(player, "&c你已达到删除上限 (" + maxDeleteTimes + ")！");
                return true;
            }

            World mainWorld = Bukkit.getWorlds().get(0);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (islandManager.isPlayerOnIsland(p, island)) {
                    p.teleport(mainWorld.getSpawnLocation());
                    MessageUtil.sendMessage(p, "&c由于岛屿被删除，你已被传送到出生点。");
                }
            }

            islandManager.removeIslandFromMemory(island);
            MessageUtil.sendMessage(player, "&a岛屿删除操作已开始异步执行，请稍候...");
            MessageUtil.sendMessage(player, "&e清理过程将在后台进行，不会影响服务器性能。");

            IslandDeleteTask deleteTask = new IslandDeleteTask(plugin, islandManager, island, player.getUniqueId(),
                    deleteCount, maxDeleteTimes);
            deleteTask.runTaskAsynchronously(plugin);
            return true;
        }

        // ====================== 邀请玩家 ======================
        if (args[0].equalsIgnoreCase("invite")) {
            Optional<Island> optionalIsland = islandManager.getIsland(player.getUniqueId());
            if (optionalIsland.isEmpty()) {
                MessageUtil.sendMessage(player, "&c你还没有岛屿！");
                return true;
            }

            Island island = optionalIsland.get();
            if (!plugin.getPermissionCoordinator().canManageMembers(island, player.getUniqueId())) {
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
                MessageUtil.sendMessage(player, "&a已向 &e" + targetPlayer.getName() + " &a发送岛屿邀请！");
                MessageUtil.sendMessage(player, "&7等待对方确认...");
            } else {
                MessageUtil.sendMessage(player, "&c邀请失败！该玩家可能已有岛屿或已有待处理的邀请。");
            }
            return true;
        }

        // ====================== 踢出成员 ======================
        if (args[0].equalsIgnoreCase("kick")) {
            Optional<Island> optionalIsland = islandManager.getIsland(player.getUniqueId());
            if (optionalIsland.isEmpty()) {
                MessageUtil.sendMessage(player, "&c你还没有岛屿！");
                return true;
            }

            Island island = optionalIsland.get();
            if (!plugin.getPermissionCoordinator().canManageMembers(island, player.getUniqueId())) {
                MessageUtil.sendMessage(player, "&c你没有权限踢出成员！");
                return true;
            }

            if (args.length < 2) {
                MessageUtil.sendMessage(player, "&c用法: /is kick <玩家名>");
                return true;
            }

            Player targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                MessageUtil.sendMessage(player, "&c玩家不存在或不在线！");
                return true;
            }

            if (targetPlayer.getUniqueId().equals(island.getOwnerId())) {
                MessageUtil.sendMessage(player, "&c你不能踢出岛主！");
                return true;
            }

            if (islandManager.removeMemberFromIsland(island.getId(), targetPlayer.getUniqueId())) {
                MessageUtil.sendMessage(player, "&a成功踢出 &e" + targetPlayer.getName() + " &a从岛屿");
                MessageUtil.sendMessage(targetPlayer, "&c你已被 &e" + player.getName() + " &c从岛屿踢出");
            } else {
                MessageUtil.sendMessage(player, "&c踢出失败，该玩家可能不是岛屿成员。");
            }
            return true;
        }

        // ====================== 晋升 / 降级 ======================
        if (args[0].equalsIgnoreCase("promote") || args[0].equalsIgnoreCase("demote")) {
            Optional<Island> optionalIsland = islandManager.getIsland(player.getUniqueId());
            if (optionalIsland.isEmpty()) {
                MessageUtil.sendMessage(player, "&c你还没有岛屿！");
                return true;
            }

            Island island = optionalIsland.get();
            if (!plugin.getPermissionCoordinator().canManageMembers(island, player.getUniqueId())) {
                MessageUtil.sendMessage(player, "&c你没有权限管理成员角色！");
                return true;
            }

            if (args.length < 2) {
                MessageUtil.sendMessage(player, "&c用法: /is " + args[0] + " <玩家名>");
                return true;
            }

            Player targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                MessageUtil.sendMessage(player, "&c玩家不存在或不在线！");
                return true;
            }

            IslandPermissionLevel currentRole = island.getMemberRole(targetPlayer.getUniqueId());
            IslandPermissionLevel newRole;

            if (args[0].equalsIgnoreCase("promote")) {
                switch (currentRole) {
                    case VISITOR -> newRole = IslandPermissionLevel.COOP;
                    case COOP -> newRole = IslandPermissionLevel.MEMBER;
                    case MEMBER -> newRole = IslandPermissionLevel.MOD;
                    case MOD -> newRole = IslandPermissionLevel.ADMIN;
                    default -> {
                        MessageUtil.sendMessage(player, "&c该玩家已经是最高角色！");
                        return true;
                    }
                }
            } else {
                switch (currentRole) {
                    case ADMIN -> newRole = IslandPermissionLevel.MOD;
                    case MOD -> newRole = IslandPermissionLevel.MEMBER;
                    case MEMBER -> newRole = IslandPermissionLevel.COOP;
                    case COOP -> newRole = IslandPermissionLevel.VISITOR;
                    default -> {
                        MessageUtil.sendMessage(player, "&c该玩家已经是最低角色！");
                        return true;
                    }
                }
            }

            if (islandManager.updateMemberRole(island.getId(), targetPlayer.getUniqueId(), newRole)) {
                String action = args[0].equalsIgnoreCase("promote") ? "晋升" : "降级";
                MessageUtil.sendMessage(player,
                        "&a成功" + action + " &e" + targetPlayer.getName() + " &a为 &e" + newRole.getDisplayName());
                MessageUtil.sendMessage(targetPlayer,
                        "&a你的岛屿角色已被 &e" + player.getName() + " &a" + action + "为 &e" + newRole.getDisplayName());
            } else {
                MessageUtil.sendMessage(player, "&c操作失败，请稍后重试。");
            }
            return true;
        }

        // ====================== 查看成员列表 ======================
        if (args[0].equalsIgnoreCase("members")) {
            Optional<Island> optionalIsland = islandManager.getIsland(player.getUniqueId());
            if (optionalIsland.isEmpty()) {
                MessageUtil.sendMessage(player, "&c你还没有岛屿！");
                return true;
            }

            Island island = optionalIsland.get();
            MessageUtil.sendMessage(player, "&a=== 岛屿成员列表 ===");

            Player owner = Bukkit.getPlayer(island.getOwnerId());
            if (owner != null) {
                MessageUtil.sendMessage(player, "&6岛主: &e" + owner.getName() + " &6(OWNER)");
            } else {
                MessageUtil.sendMessage(player, "&6岛主: &e" + island.getOwnerId() + " &6(OWNER - 离线)");
            }

            for (Map.Entry<UUID, IslandPermissionLevel> entry : island.getMembers().entrySet()) {
                Player member = Bukkit.getPlayer(entry.getKey());
                String status = member != null ? "" : " - 离线";
                MessageUtil.sendMessage(player, "&b成员: &e" + (member != null ? member.getName() : entry.getKey()) +
                        " &b(" + entry.getValue().getDisplayName() + ")" + status);
            }

            if (island.getMembers().isEmpty()) {
                MessageUtil.sendMessage(player, "&7暂无其他成员");
            }
            return true;
        }

        // ====================== 查看自身角色 ======================
        if (args[0].equalsIgnoreCase("role")) {
            Optional<Island> optionalIsland = islandManager.getIsland(player.getUniqueId());
            if (optionalIsland.isEmpty()) {
                MessageUtil.sendMessage(player, "&c你还没有岛屿！");
                return true;
            }

            Island island = optionalIsland.get();
            IslandPermissionLevel playerRole = island.getMemberRole(player.getUniqueId());
            MessageUtil.sendMessage(player, "&a你的岛屿角色: &e" + playerRole.getDisplayName());
            MessageUtil.sendMessage(player, "&a权限描述: &7" + playerRole.getDescription());
            return true;
        }

        // ====================== 权限管理命令 ======================
        if (args[0].equalsIgnoreCase("permission")) {
            return permissionCommand.handlePermissionCommand(player, args);
        }

        // ====================== 查看所有可用权限列表 ======================
        if (args[0].equalsIgnoreCase("permissions")) {
            return permissionCommand.handlePermissionsListCommand(player);
        }

        // ====================== 接受邀请 ======================
        if (args[0].equalsIgnoreCase("accept")) {
            InvitationManager invitationManager = plugin.getInvitationManager();

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

        // ====================== 拒绝邀请 ======================
        if (args[0].equalsIgnoreCase("decline")) {
            InvitationManager invitationManager = plugin.getInvitationManager();

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

        MessageUtil.sendMessage(player, "&c未知命令。输入 /is help 查看帮助。");
        return true;
    }

    private void sendHelpMessage(Player player) {
        MessageUtil.sendMessage(player, "&a=== StarMSkyblock 帮助 ===");
        MessageUtil.sendMessage(player, "&b/is create [类型] [名称] &f- 创建你的空岛");
        MessageUtil.sendMessage(player, "&b/is home [confirm] &f- 传送到你的空岛（confirm强制传送）");
        MessageUtil.sendMessage(player, "&b/is sethome &f- 设置岛屿传送点（脚下不能是空气）");
        MessageUtil.sendMessage(player, "&b/is border &f- 切换显示岛屿边界");
        MessageUtil.sendMessage(player, "&b/is delete [confirm] &f- 删除你的空岛");
        MessageUtil.sendMessage(player, "&b/is invite <玩家> &f- 邀请玩家加入岛屿");
        MessageUtil.sendMessage(player, "&b/is accept &f- 接受岛屿邀请");
        MessageUtil.sendMessage(player, "&b/is decline &f- 拒绝岛屿邀请");
        MessageUtil.sendMessage(player, "&b/is kick <玩家> &f- 踢出岛屿成员");
        MessageUtil.sendMessage(player, "&b/is promote <玩家> &f- 晋升成员角色");
        MessageUtil.sendMessage(player, "&b/is demote <玩家> &f- 降级成员角色");
        MessageUtil.sendMessage(player, "&b/is members &f- 查看岛屿成员列表");
        MessageUtil.sendMessage(player, "&b/is role &f- 查看自己的岛屿角色");
        MessageUtil.sendMessage(player, "&b/is permission <权限> <等级> &f- 设置权限最低等级");
        MessageUtil.sendMessage(player, "&b/is permissions &f- 查看所有可用权限列表");
    }

    private boolean isLocationSafe(Location location) {
        Location blockBelowLocation = location.clone().subtract(0, 1, 0);
        return !blockBelowLocation.getBlock().getType().isAir();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return subCommands.stream()
                    .filter(sub -> sub.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        List<String> permissionCompletions = permissionCommand.onTabComplete(args);
        if (permissionCompletions != null) {
            return permissionCompletions;
        }

        return new ArrayList<>();
    }
}