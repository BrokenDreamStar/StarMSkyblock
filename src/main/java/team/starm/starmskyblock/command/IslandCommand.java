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
import team.starm.starmskyblock.setting.IslandSetting;
import team.starm.starmskyblock.island.InvitationManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.util.ColorUtil;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            "invite", "remove", "promote", "demote", "rename", "coop",
            "members", "coops", "mycoops", "myperms", "role", "tp",
            "accept", "decline", "permission", "settings");

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

        // 提取 -s 静默标记
        boolean silent = args.length > 0 && (args[args.length - 1].equals("-s"));
        if (silent) {
            args = java.util.Arrays.copyOf(args, args.length - 1);
            ColorUtil.setSilent(player.getUniqueId(), true);
        }

        try {
            return handleCommand(player, args);
        } finally {
            if (silent) {
                ColorUtil.setSilent(player.getUniqueId(), false);
            }
        }
    }

    private boolean handleCommand(Player player, String[] args) {
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
            Optional<Island> optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
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

        // ====================== 传送到指定岛屿 ======================
        if (args[0].equalsIgnoreCase("tp")) {
            if (args.length < 2) {
                MessageUtil.sendMessage(player, "&c用法: /is tp <岛屿名称> [岛屿ID] [confirm]");
                return true;
            }

            String islandName = args[1];
            java.util.List<Island> matchingIslands = islandManager.getIslandsByName(islandName);

            if (matchingIslands.isEmpty()) {
                MessageUtil.sendMessage(player, "&c未找到名称为 &e" + islandName + " &c的岛屿！");
                return true;
            }

            Island targetIsland;
            int confirmArgIndex;

            if (matchingIslands.size() > 1) {
                if (args.length < 3 || args[2].equalsIgnoreCase("confirm")) {
                    showIslandList(player, matchingIslands);
                    return true;
                }
                try {
                    int islandId = Integer.parseInt(args[2]);
                    java.util.Optional<Island> matched = matchingIslands.stream()
                            .filter(i -> i.getId() == islandId)
                            .findFirst();
                    if (matched.isEmpty()) {
                        MessageUtil.sendMessage(player, "&c未找到ID为 &e" + islandId + " &c的匹配岛屿！");
                        showIslandList(player, matchingIslands);
                        return true;
                    }
                    targetIsland = matched.get();
                    confirmArgIndex = 3;
                } catch (NumberFormatException e) {
                    showIslandList(player, matchingIslands);
                    return true;
                }
            } else {
                targetIsland = matchingIslands.get(0);
                confirmArgIndex = 2;
            }

            if (!targetIsland.getSetting(IslandSetting.TP)) {
                MessageUtil.sendMessage(player, "&c该岛屿未开放传送！");
                return true;
            }

            ConfigManager config = plugin.getConfigManager();
            Location spawnLocation;
            World targetWorld;

            if (targetIsland.hasCustomHome()) {
                Island.WorldType worldType = targetIsland.getCustomHomeWorldType();
                targetWorld = switch (worldType) {
                    case NETHER -> worldManager.getSkyblockNether();
                    case END -> worldManager.getSkyblockEnd();
                    default -> worldManager.getSkyblockWorld();
                };
                spawnLocation = new Location(targetWorld,
                        targetIsland.getCustomHomeX(),
                        targetIsland.getCustomHomeY(),
                        targetIsland.getCustomHomeZ());
            } else {
                targetWorld = worldManager.getSkyblockWorld();
                double[] offsets = config.getTeleportOffsetsBySchematicAndWorldType(
                        targetIsland.getSchematicId(), Island.WorldType.NORMAL);
                double teleportX = (targetIsland.getCenterChunkX() * 16) + 8 + offsets[0];
                double teleportY = config.getIslandHeight() + offsets[1];
                double teleportZ = (targetIsland.getCenterChunkZ() * 16) + 8 + offsets[2];
                spawnLocation = new Location(targetWorld, teleportX, teleportY, teleportZ);
            }

            boolean confirmed = args.length > confirmArgIndex && args[confirmArgIndex].equalsIgnoreCase("confirm");
            if (!isLocationSafe(spawnLocation) && !confirmed) {
                MessageUtil.sendMessage(player, "&c警告：该岛屿传送点不安全！脚下可能是空气。");
                MessageUtil.sendMessage(player, "&c使用 &e/is tp " + islandName + " confirm &c强制传送（可能摔落）。");
                return true;
            }

            player.teleport(spawnLocation);
            MessageUtil.sendMessage(player, "&a已传送到岛屿 &e" + targetIsland.getName() + "&a！");
            return true;
        }

        // ====================== 切换岛屿边界显示 ======================
        if (args[0].equalsIgnoreCase("border")) {
            if (args.length < 2) {
                // 无参数时显示当前状态
                boolean current = plugin.getBorderListener().isPlayerShowBorder(player.getUniqueId());
                MessageUtil.sendMessage(player, "&a岛屿边界显示: " + (current ? "&a已开启" : "&c已关闭"));
                MessageUtil.sendMessage(player, "&7使用 &e/is border toggle &7切换，或 &e/is border <true|false> &7直接设置。");
                return true;
            }

            boolean show;
            if (args[1].equalsIgnoreCase("toggle")) {
                show = !plugin.getBorderListener().isPlayerShowBorder(player.getUniqueId());
            } else if (args[1].equalsIgnoreCase("true")) {
                show = true;
            } else if (args[1].equalsIgnoreCase("false")) {
                show = false;
            } else {
                MessageUtil.sendMessage(player, "&c用法: /is border [true|false|toggle]");
                return true;
            }

            plugin.getBorderListener().setPlayerShowBorder(player.getUniqueId(), show);

            if (show) {
                MessageUtil.sendMessage(player, "&a岛屿边界显示已开启！");
            } else {
                MessageUtil.sendMessage(player, "&c岛屿边界显示已关闭！");
            }

            plugin.getBorderListener().updatePlayerBorder(player);
            return true;
        }

        // ====================== 设置自定义传送点 ======================
        if (args[0].equalsIgnoreCase("sethome")) {
            Optional<Island> optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
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

            if (!islandManager.isPlayerOnIsland(player, island)) {
                MessageUtil.sendMessage(player, "&c你只能在你的岛屿范围内设置传送点！");
                return true;
            }

            if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.SET_HOME)) {
                MessageUtil.sendMessage(player, "&c你没有权限设置传送点！");
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
                MessageUtil.sendMessage(player, "&a已成功设置传送点！");
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
            if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.DELETE_ISLAND)) {
                MessageUtil.sendMessage(player, "&c你没有权限删除岛屿！");
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
                    MessageUtil.sendMessage(p, "&c由于当前所在岛屿被删除，你已被传送到出生点。");
                }
            }

            islandManager.removeIslandFromMemory(island);
            MessageUtil.sendMessage(player, "&a岛屿删除操作已开始执行，请稍候...");
            MessageUtil.sendMessage(player, "&e岛屿已删除");

            IslandDeleteTask deleteTask = new IslandDeleteTask(plugin, islandManager, island, player.getUniqueId(),
                    deleteCount, maxDeleteTimes);
            deleteTask.runTaskAsynchronously(plugin);
            return true;
        }

        // ====================== 邀请玩家 ======================
        if (args[0].equalsIgnoreCase("invite")) {
            Optional<Island> optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
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

        // ====================== 踢出成员 ======================
        if (args[0].equalsIgnoreCase("remove")) {
            Optional<Island> optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
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

            if (islandManager.removeMemberFromIsland(island.getId(), targetUuid)) {
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

        // ====================== 晋升 / 降级 ======================
        if (args[0].equalsIgnoreCase("promote") || args[0].equalsIgnoreCase("demote")) {
            Optional<Island> optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
            if (optionalIsland.isEmpty()) {
                MessageUtil.sendMessage(player, "&c你还没有岛屿！");
                return true;
            }

            Island island = optionalIsland.get();
            if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.SET_ROLE)) {
                MessageUtil.sendMessage(player, "&c你没有权限管理成员角色！");
                return true;
            }

            if (args.length < 2) {
                MessageUtil.sendMessage(player, "&c用法: /is " + args[0] + " <玩家名>");
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

            if (targetUuid.equals(player.getUniqueId())) {
                MessageUtil.sendMessage(player, "&c你不能对自己使用此命令！");
                return true;
            }

            IslandPermissionLevel executorRole = island.getMemberRole(player.getUniqueId());
            IslandPermissionLevel targetRole = island.getMemberRole(targetUuid);

            if (executorRole.getPermissionLevel() <= targetRole.getPermissionLevel()) {
                MessageUtil.sendMessage(player, "&c你无法管理同权限或更高权限的成员！");
                return true;
            }

            IslandPermissionLevel newRole;

            if (args[0].equalsIgnoreCase("promote")) {
                switch (targetRole) {
                    case MEMBER -> newRole = IslandPermissionLevel.MOD;
                    case MOD -> newRole = IslandPermissionLevel.ADMIN;
                    case ADMIN -> {
                        MessageUtil.sendMessage(player, "&c该玩家已经是最高角色！");
                        return true;
                    }
                    default -> {
                        MessageUtil.sendMessage(player, "&c只能晋升岛员、风纪委员、管理员！");
                        return true;
                    }
                }
            } else {
                switch (targetRole) {
                    case ADMIN -> newRole = IslandPermissionLevel.MOD;
                    case MOD -> newRole = IslandPermissionLevel.MEMBER;
                    case MEMBER -> {
                        MessageUtil.sendMessage(player, "&c该玩家已经是最低角色！");
                        return true;
                    }
                    default -> {
                        MessageUtil.sendMessage(player, "&c只能降级岛员、风纪委员、管理员！");
                        return true;
                    }
                }
            }

            if (islandManager.updateMemberRole(island.getId(), targetUuid, newRole)) {
                String action = args[0].equalsIgnoreCase("promote") ? "晋升" : "降级";
                MessageUtil.sendMessage(player,
                        "&a成功" + action + " &e" + targetName + " &a为 &e" + newRole.getDisplayName());
                Player targetPlayer = Bukkit.getPlayer(targetUuid);
                if (targetPlayer != null) {
                    MessageUtil.sendMessage(targetPlayer,
                            "&a你的岛屿角色已被 &e" + player.getName() + " &a" + action + "为 &e" + newRole.getDisplayName());
                }
            } else {
                MessageUtil.sendMessage(player, "&c操作失败，请稍后重试。");
            }
            return true;
        }

        // ====================== 查看成员列表 ======================
        if (args[0].equalsIgnoreCase("members")) {
            Optional<Island> optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
            if (optionalIsland.isEmpty()) {
                MessageUtil.sendMessage(player, "&c你还没有岛屿！");
                return true;
            }

            Island island = optionalIsland.get();
            MessageUtil.sendMessage(player, "&a=== 岛屿成员列表 ===");

            // 岛主信息（优先从数据库获取名称）
            String ownerName = getPlayerName(island.getOwnerId());
            boolean ownerOnline = Bukkit.getPlayer(island.getOwnerId()) != null;
            String ownerStatus = ownerOnline ? "" : " &7(离线)";
            MessageUtil.sendMessage(player, "&6岛主: &e" + ownerName + " &6("
                    + IslandPermissionLevel.OWNER.getDisplayName() + ")" + ownerStatus);

            // 成员信息
            for (Map.Entry<UUID, IslandPermissionLevel> entry : island.getMembers().entrySet()) {
                String memberName = getPlayerName(entry.getKey());
                boolean memberOnline = Bukkit.getPlayer(entry.getKey()) != null;
                String status = memberOnline ? "" : " &7(离线)";
                MessageUtil.sendMessage(player, "&b成员: &e" + memberName +
                        " &b(" + entry.getValue().getDisplayName() + ")" + status);
            }

            if (island.getMembers().isEmpty()) {
                MessageUtil.sendMessage(player, "&7暂无其他成员");
            }
            return true;
        }

        // ====================== 查看合作者列表 ======================
        if (args[0].equalsIgnoreCase("coops")) {
            Optional<Island> optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
            if (optionalIsland.isEmpty()) {
                MessageUtil.sendMessage(player, "&c你还没有岛屿！");
                return true;
            }

            Island island = optionalIsland.get();
            MessageUtil.sendMessage(player, "&a=== 合作者列表 ===");

            if (island.getCoops().isEmpty()) {
                MessageUtil.sendMessage(player, "&7暂无合作者");
                return true;
            }

            for (UUID coopUuid : island.getCoops()) {
                String coopName = getPlayerName(coopUuid);
                boolean coopOnline = Bukkit.getPlayer(coopUuid) != null;
                String status = coopOnline ? "" : " &7(离线)";
                MessageUtil.sendMessage(player, "&b合作者: &e" + coopName + status);
            }
            return true;
        }

        // ====================== 查看自己的合作者身份 ======================
        if (args[0].equalsIgnoreCase("mycoops")) {
            java.util.List<Island> coopIslands = islandManager.getIslandsByCoop(player.getUniqueId());
            if (coopIslands.isEmpty()) {
                MessageUtil.sendMessage(player, "&c你不是任何岛屿的合作者！");
                return true;
            }

            MessageUtil.sendMessage(player, "&a=== 我的合作者身份 ===");
            for (Island coopIsland : coopIslands) {
                String ownerName = getPlayerName(coopIsland.getOwnerId());
                boolean ownerOnline = Bukkit.getPlayer(coopIsland.getOwnerId()) != null;
                String status = ownerOnline ? "" : " &7(离线)";
                MessageUtil.sendMessage(player, "&b岛屿 #" + coopIsland.getId() + " &e(岛主: " + ownerName + ")" + status);
            }
            return true;
        }

        // ====================== 查看自己拥有的权限 ======================
        if (args[0].equalsIgnoreCase("myperms")) {
            Optional<Island> optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
            if (optionalIsland.isEmpty()) {
                MessageUtil.sendMessage(player, "&c你还没有岛屿！");
                return true;
            }

            Island island = optionalIsland.get();
            IslandPermissionLevel playerRole = island.getMemberRole(player.getUniqueId());
            MessageUtil.sendMessage(player, "&a=== 我的岛屿权限 ===");
            MessageUtil.sendMessage(player, "&7当前角色: &e" + playerRole.getDisplayName() + " &7(等级 " + playerRole.getPermissionLevel() + ")");
            MessageUtil.sendMessage(player, "");

            IslandPermission[] permissions = IslandPermission.values();
            for (int i = 0; i < permissions.length; i++) {
                IslandPermission perm = permissions[i];
                if (perm == IslandPermission.ALL) continue;
                boolean hasPerm = island.hasPermission(player.getUniqueId(), perm);
                String icon = hasPerm ? "&a✔" : "&c✘";
                MessageUtil.sendMessage(player, icon + " &7" + perm.getDisplayName());
            }
            return true;
        }

        // ====================== 查看自身角色 ======================
        if (args[0].equalsIgnoreCase("role")) {
            Optional<Island> optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
            if (optionalIsland.isEmpty()) {
                MessageUtil.sendMessage(player, "&c你还没有岛屿！");
                return true;
            }

            Island island = optionalIsland.get();
            IslandPermissionLevel playerRole = island.getMemberRole(player.getUniqueId());
            MessageUtil.sendMessage(player, "&a你的岛屿角色: &e" + playerRole.getDisplayName());
            return true;
        }

        // ====================== 修改岛屿名称 ======================
        if (args[0].equalsIgnoreCase("rename")) {
            Optional<Island> optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
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

            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(args[i]);
            }
            String newName = sb.toString();

            if (newName.length() > 32) {
                MessageUtil.sendMessage(player, "&c岛屿名称不能超过32个字符！");
                return true;
            }

            if (islandManager.updateIslandName(island.getId(), newName)) {
                MessageUtil.sendMessage(player, "&a岛屿名称已修改为: &e" + newName);
            } else {
                MessageUtil.sendMessage(player, "&c修改失败，请稍后重试。");
            }
            return true;
        }

        // ====================== 合作者管理（COOP） ======================
        if (args[0].equalsIgnoreCase("coop")) {
            if (args.length < 2) {
                MessageUtil.sendMessage(player, "&c用法:");
                MessageUtil.sendMessage(player, "&b/is coop add <玩家> &f- 添加合作者");
                MessageUtil.sendMessage(player, "&b/is coop remove <玩家> &f- 移除合作者");
                return true;
            }

            if (args[1].equalsIgnoreCase("add")) {
                return handleCoopAdd(player, args, islandManager);
            } else if (args[1].equalsIgnoreCase("remove")) {
                return handleCoopRemove(player, args, islandManager);
            } else {
                MessageUtil.sendMessage(player, "&c未知的子命令: &e" + args[1]);
                MessageUtil.sendMessage(player, "&c用法: /is coop add|remove <玩家名>");
                return true;
            }
        }

        // ====================== 权限管理命令 ======================
        if (args[0].equalsIgnoreCase("permission")) {
            return permissionCommand.handlePermissionCommand(player, args);
        }

        // ====================== 岛屿设置 ======================
        if (args[0].equalsIgnoreCase("settings")) {
            return handleSettingsCommand(player, args, islandManager);
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

    private boolean handleSettingsCommand(Player player, String[] args, IslandManager islandManager) {
        Optional<Island> optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();

        // 权限检查
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.EDIT_SETTINGS)) {
            MessageUtil.sendMessage(player, "&c你没有权限修改岛屿设置！");
            return true;
        }

        // /is settings - 显示当前设置列表
        if (args.length == 1) {
            return displaySettings(player, island);
        }

        // /is settings <setting> [true|false|toggle]
        String settingKey = args[1].toLowerCase();

        // 先验证设置项是否存在
        IslandSetting setting = IslandSetting.fromKey(settingKey);
        if (setting == null) {
            MessageUtil.sendMessage(player, "&c未知的设置项: &e" + settingKey);
            MessageUtil.sendMessage(player, "&c可用设置项: &e/is settings &7查看所有设置");
            return true;
        }

        boolean currentVal = island.getSetting(setting);

        if (args.length < 3) {
            // 无参数时显示当前状态
            String status = currentVal ? "&a已启用" : "&c已禁用";
            MessageUtil.sendMessage(player, "&e" + setting.getDisplayName() + " &f| " + status);
            MessageUtil.sendMessage(player, "&7使用 &e/is settings " + settingKey + " toggle &7切换，或 &e/is settings " + settingKey + " <true|false> &7直接设置。");
            return true;
        }

        boolean value;
        if (args[2].equalsIgnoreCase("toggle")) {
            value = !currentVal;
        } else if (args[2].equalsIgnoreCase("true")) {
            value = true;
        } else if (args[2].equalsIgnoreCase("false")) {
            value = false;
        } else {
            MessageUtil.sendMessage(player, "&c值必须为 true 或 false！");
            return true;
        }

        // 修改设置
        island.setSetting(setting, value);

        if (islandManager.updateIslandSettings(island.getId(), island)) {
            MessageUtil.sendMessage(player, "&a设置项 &e" + setting.getDisplayName() + " &a已" + (value ? "&a启用" : "&c禁用"));
        } else {
            MessageUtil.sendMessage(player, "&c设置保存失败，请稍后重试。");
        }
        return true;
    }

    private boolean displaySettings(Player player, Island island) {
        MessageUtil.sendMessage(player, "&a=== 岛屿设置 ===");

        for (IslandSetting setting : IslandSetting.values()) {
            boolean val = island.getSetting(setting);
            String status = val ? "&a✔ 启用" : "&c✘ 禁用";
            MessageUtil.sendMessage(player, "&e" + setting.getDisplayName() + " &7(" + setting.getConfigKey() + ")" + " &f| " + status);
        }
        MessageUtil.sendMessage(player, "&7使用 &e/is settings <设置项> <true|false> &7来修改设置。");
        return true;
    }

    private boolean handleCoopAdd(Player player, String[] args, IslandManager islandManager) {
        Optional<Island> optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.INVITE_COOP)) {
            MessageUtil.sendMessage(player, "&c你没有权限邀请合作者！");
            return true;
        }

        if (args.length < 3) {
            MessageUtil.sendMessage(player, "&c用法: /is coop add <玩家名>");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[2]);
        if (targetPlayer == null) {
            MessageUtil.sendMessage(player, "&c玩家不存在或不在线！");
            return true;
        }

        if (targetPlayer.getUniqueId().equals(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&c你不能将自己添加为合作者！");
            return true;
        }

        if (islandManager.getIslandByPlayer(targetPlayer.getUniqueId()).isEmpty()) {
            MessageUtil.sendMessage(player, "&c该玩家没有岛屿，无法添加为合作者！");
            return true;
        }

        if (island.isCoop(targetPlayer.getUniqueId())) {
            MessageUtil.sendMessage(player, "&c该玩家已经是合作者！");
            return true;
        }

        if (island.getMembers().containsKey(targetPlayer.getUniqueId())) {
            MessageUtil.sendMessage(player, "&c该玩家已经是岛屿成员，无法添加为合作者！");
            return true;
        }

        if (islandManager.addCoopToIsland(island.getId(), targetPlayer.getUniqueId())) {
            MessageUtil.sendMessage(player, "&a已将 &e" + targetPlayer.getName() + " &a添加为合作者！");
            MessageUtil.sendMessage(targetPlayer, "&a你已被 &e" + player.getName() + " &a添加为岛屿合作者！");
        } else {
            MessageUtil.sendMessage(player, "&c操作失败，请稍后重试。");
        }
        return true;
    }

    private boolean handleCoopRemove(Player player, String[] args, IslandManager islandManager) {
        Optional<Island> optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.REMOVE_COOP)) {
            MessageUtil.sendMessage(player, "&c你没有权限移除合作者！");
            return true;
        }

        if (args.length < 3) {
            MessageUtil.sendMessage(player, "&c用法: /is coop remove <玩家名>");
            return true;
        }

        UUID targetUuid = island.getCoops().stream()
                .filter(uuid -> {
                    String name = getPlayerName(uuid);
                    return name != null && name.equalsIgnoreCase(args[2]);
                })
                .findFirst().orElse(null);
        if (targetUuid == null) {
            MessageUtil.sendMessage(player, "&c该玩家不是合作者！");
            return true;
        }

        String targetName = getPlayerName(targetUuid);

        if (targetUuid.equals(island.getOwnerId())) {
            MessageUtil.sendMessage(player, "&c你不能移除岛主！");
            return true;
        }

        if (islandManager.removeCoopFromIsland(island.getId(), targetUuid)) {
            MessageUtil.sendMessage(player, "&a已移除合作者 &e" + targetName);
            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null) {
                MessageUtil.sendMessage(targetPlayer, "&c你已被 &e" + player.getName() + " &c移出岛屿合作者队伍");
            }
        } else {
            MessageUtil.sendMessage(player, "&c操作失败，请稍后重试。");
        }
        return true;
    }

    private void showIslandList(Player player, java.util.List<Island> islands) {
        MessageUtil.sendMessage(player, "&a找到多个同名岛屿：");
        for (Island island : islands) {
            String ownerName = getPlayerName(island.getOwnerId());
            MessageUtil.sendMessage(player, "  &e#" + island.getId() + " &7- &f" + ownerName);
        }
        MessageUtil.sendMessage(player, "&7使用 &e/is tp <名称> <ID> &7指定具体岛屿");
    }

    private void sendHelpMessage(Player player) {
        MessageUtil.sendMessage(player, "&a=== StarMSkyblock 帮助 ===");
        MessageUtil.sendMessage(player, "&b/is create [类型] [名称] &f- 创建你的空岛");
        MessageUtil.sendMessage(player, "&b/is home [confirm] &f- 传送到你的空岛（confirm强制传送）");
        MessageUtil.sendMessage(player, "&b/is tp <岛屿名称> [岛屿ID] [confirm] &f- 传送到指定岛屿");
        MessageUtil.sendMessage(player, "&b/is sethome &f- 设置岛屿传送点（脚下不能是空气）");
        MessageUtil.sendMessage(player, "&b/is border [true|false|toggle] &f- 开启/关闭/切换岛屿边界显示");
        MessageUtil.sendMessage(player, "&b/is delete [confirm] &f- 删除你的空岛");
        MessageUtil.sendMessage(player, "&b/is invite <玩家> &f- 邀请玩家加入岛屿");
        MessageUtil.sendMessage(player, "&b/is accept &f- 接受岛屿邀请");
        MessageUtil.sendMessage(player, "&b/is decline &f- 拒绝岛屿邀请");
        MessageUtil.sendMessage(player, "&b/is remove <玩家> [confirm] &f- 踢出岛屿成员");
        MessageUtil.sendMessage(player, "&b/is promote <玩家> &f- 晋升成员角色");
        MessageUtil.sendMessage(player, "&b/is demote <玩家> &f- 降级成员角色");
        MessageUtil.sendMessage(player, "&b/is members &f- 查看岛屿成员列表");
        MessageUtil.sendMessage(player, "&b/is coops &f- 查看合作者列表");
        MessageUtil.sendMessage(player, "&b/is mycoops &f- 查看自己是哪些岛屿的合作者");
        MessageUtil.sendMessage(player, "&b/is role &f- 查看自己的岛屿角色");
        MessageUtil.sendMessage(player, "&b/is rename <名称> &f- 修改岛屿名称");
        MessageUtil.sendMessage(player, "&b/is coop add <玩家> &f- 添加合作者");
        MessageUtil.sendMessage(player, "&b/is coop remove <玩家> &f- 移除合作者");
        MessageUtil.sendMessage(player, "&b/is myperms &f- 查看自己拥有的权限");
        MessageUtil.sendMessage(player, "&b/is permission <权限> <等级> &f- 设置权限最低等级");
        MessageUtil.sendMessage(player, "&b/is settings &f- 查看岛屿设置");
        MessageUtil.sendMessage(player, "&b/is settings <设置项> <true|false> &f- 修改岛屿设置");
    }

    private boolean isLocationSafe(Location location) {
        Location blockBelowLocation = location.clone().subtract(0, 1, 0);
        return !blockBelowLocation.getBlock().getType().isAir();
    }

    /**
     * 获取玩家显示名称：优先从数据库获取，回退到 OfflinePlayer API
     */
    private String getPlayerName(UUID uuid) {
        Optional<String> dbName = plugin.getSqliteManager().getPlayerName(uuid);
        if (dbName.isPresent()) {
            return dbName.get();
        }
        // 回退到 OfflinePlayer API，并存入数据库以备后续使用
        String offlineName = Bukkit.getOfflinePlayer(uuid).getName();
        if (offlineName != null) {
            plugin.getSqliteManager().savePlayerName(uuid, offlineName);
            return offlineName;
        }
        return uuid.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return subCommands.stream()
                    .filter(sub -> sub.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("border")) {
            String prefix = args[1].toLowerCase();
            return Arrays.asList("true", "false", "toggle").stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("settings")) {
            String prefix = args[1].toLowerCase();
            return Arrays.stream(IslandSetting.values())
                    .map(IslandSetting::getConfigKey)
                    .filter(key -> key.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("settings")) {
            String prefix = args[2].toLowerCase();
            return Arrays.asList("true", "false").stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("coop")) {
            String prefix = args[1].toLowerCase();
            return Arrays.asList("add", "remove").stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("coop") && args[1].equalsIgnoreCase("add")) {
            String prefix = args[2].toLowerCase();
            Optional<Island> optionalIsland = plugin.getIslandManager().getIsland(player.getUniqueId());
            if (optionalIsland.isPresent()) {
                Island island = optionalIsland.get();
                IslandManager im = plugin.getIslandManager();
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> {
                            Player p = Bukkit.getPlayer(name);
                            if (p == null) return false;
                            if (p.getUniqueId().equals(player.getUniqueId())) return false;
                            if (island.isCoop(p.getUniqueId())) return false;
                            return im.getIslandByPlayer(p.getUniqueId()).isPresent();
                        })
                        .filter(name -> name.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("coop") && args[1].equalsIgnoreCase("remove")) {
            String prefix = args[2].toLowerCase();
            Optional<Island> optionalIsland = plugin.getIslandManager().getIsland(player.getUniqueId());
            if (optionalIsland.isPresent()) {
                return optionalIsland.get().getCoops().stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 2) {
            String prefix = args[1].toLowerCase();

            if (args[0].equalsIgnoreCase("invite")) {
                Optional<Island> optionalIsland = plugin.getIslandManager().getIsland(player.getUniqueId());
                if (optionalIsland.isPresent()) {
                    Island island = optionalIsland.get();
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> {
                                Player p = Bukkit.getPlayer(name);
                                if (p == null) return false;
                                if (p.getUniqueId().equals(player.getUniqueId())) return false;
                                return island.getMemberRole(p.getUniqueId()) == IslandPermissionLevel.VISITOR;
                            })
                            .filter(name -> name.toLowerCase().startsWith(prefix))
                            .collect(Collectors.toList());
                }
            }

            if (args[0].equalsIgnoreCase("tp")) {
                if (args.length == 2) {
                    return plugin.getIslandManager().getAllIslands().stream()
                            .map(Island::getName)
                            .filter(name -> name != null && !name.isEmpty())
                            .filter(name -> name.toLowerCase().startsWith(prefix))
                            .distinct()
                            .collect(Collectors.toList());
                }
                if (args.length == 3) {
                    String name = args[1];
                    java.util.List<Island> matches = plugin.getIslandManager().getIslandsByName(name);
                    if (matches.size() > 1) {
                        String idPrefix = args[2];
                        return matches.stream()
                                .map(i -> String.valueOf(i.getId()))
                                .filter(id -> id.startsWith(idPrefix))
                                .collect(Collectors.toList());
                    }
                }
            }

            if (args[0].equalsIgnoreCase("remove")
                    || args[0].equalsIgnoreCase("promote") || args[0].equalsIgnoreCase("demote")) {
                Optional<Island> optionalIsland = plugin.getIslandManager().getIsland(player.getUniqueId());
                if (optionalIsland.isPresent()) {
                    Island island = optionalIsland.get();
                    IslandPermissionLevel executorRole = island.getMemberRole(player.getUniqueId());
                    return island.getMembers().entrySet().stream()
                            .filter(e -> e.getValue().getPermissionLevel() < executorRole.getPermissionLevel())
                            .map(e -> getPlayerName(e.getKey()))
                            .filter(Objects::nonNull)
                            .filter(name -> name.toLowerCase().startsWith(prefix))
                            .collect(Collectors.toList());
                }
            }
        }

        List<String> permissionCompletions = permissionCommand.onTabComplete(args);
        if (permissionCompletions != null) {
            return permissionCompletions;
        }

        return new ArrayList<>();
    }
}
