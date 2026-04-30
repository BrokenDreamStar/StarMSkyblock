package team.starm.starmskyblock.command;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class IslandPermissionCommand {

    private final StarMSkyblock plugin;

    public IslandPermissionCommand(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public boolean handlePermissionCommand(Player player, String[] args) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIsland(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();

        if (!island.hasPermission(player.getUniqueId(), IslandPermission.SET_PERMISSIONS)) {
            MessageUtil.sendMessage(player, "&c你没有权限管理岛屿权限！");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&c用法: /is permission <权限> [角色/等级]");
            MessageUtil.sendMessage(player, "&c示例: /is permission BUILD 2");
            MessageUtil.sendMessage(player, "&c查询当前配置: /is permission BUILD");
            return true;
        }

        String permissionName = args[1].toUpperCase();

        if (args.length == 2) {
            return showPermissionConfig(player, island, permissionName);
        }

        return setPermissionLevel(player, island, args, permissionName);
    }

    private boolean showPermissionConfig(Player player, Island island, String permissionName) {
        IslandPermission permission;
        try {
            permission = IslandPermission.valueOf(permissionName);
        } catch (IllegalArgumentException e) {
            MessageUtil.sendMessage(player, "&c无效的权限: " + permissionName);
            MessageUtil.sendMessage(player, "&c使用 /is permissions 查看所有可用权限");
            return true;
        }

        MessageUtil.sendMessage(player, "&a=== 权限 " + permissionName + " 当前配置 ===");

        boolean hasCustom = false;
        for (IslandPermissionLevel role : IslandPermissionLevel.values()) {
            if (island.hasPermission(role, permission)) {
                MessageUtil.sendMessage(player,
                        "&e" + role.getDisplayName() + "(&6" + role.getPermissionLevel() + "&e): &a拥有");
                hasCustom = true;
            }
        }
        if (!hasCustom) {
            MessageUtil.sendMessage(player, "&7该权限使用默认配置（permissions.yml）");
        }
        return true;
    }

    private boolean setPermissionLevel(Player player, Island island, String[] args, String permissionName) {
        String roleInput = args[2];

        IslandPermission permission;
        try {
            permission = IslandPermission.valueOf(permissionName);
        } catch (IllegalArgumentException e) {
            MessageUtil.sendMessage(player, "&c无效的权限: " + permissionName);
            return true;
        }

        IslandPermissionLevel targetRole = parseTargetRole(roleInput);
        if (targetRole == null) {
            MessageUtil.sendMessage(player, "&c无效的角色: " + roleInput);
            MessageUtil.sendMessage(player,
                    "&c可用角色: OWNER(5), ADMIN(4), MOD(3), MEMBER(2), COOP(1), VISITOR(0) 或数字 0-5");
            return true;
        }

        IslandPermissionLevel playerRole = island.getMemberRole(player.getUniqueId());

        // 检查玩家是否有权限管理目标角色的权限
        // 由 SET_PERMISSIONS 权限控制，而不是硬编码限制
        if (!IslandPermissionLevel.getManageableRoles(playerRole).contains(targetRole)) {
            MessageUtil.sendMessage(player, "&c你不能管理 " + targetRole.getDisplayName() + " 角色的权限！");
            return true;
        }

        int targetLevel = targetRole.getPermissionLevel();
        IslandManager islandManager = plugin.getIslandManager();

        for (IslandPermissionLevel role : IslandPermissionLevel.values()) {
            islandManager.removePermission(island.getId(), role, permission);
        }

        for (IslandPermissionLevel role : IslandPermissionLevel.values()) {
            if (role.getPermissionLevel() >= targetLevel) {
                islandManager.addPermission(island.getId(), role, permission);
            }
        }

        sendPermissionUpdateMessage(player, permission, permissionName, targetRole, targetLevel);
        return true;
    }

    private IslandPermissionLevel parseTargetRole(String roleInput) {
        try {
            int level = Integer.parseInt(roleInput);
            if (level >= 0 && level <= 5) {
                for (IslandPermissionLevel r : IslandPermissionLevel.values()) {
                    if (r.getPermissionLevel() == level) {
                        return r;
                    }
                }
            }
        } catch (NumberFormatException ignored) {
        }

        try {
            return IslandPermissionLevel.valueOf(roleInput.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void sendPermissionUpdateMessage(Player player, IslandPermission permission,
                                             String permissionName, IslandPermissionLevel targetRole, int targetLevel) {
        if (permission == IslandPermission.ALL) {
            if (targetLevel == 5) {
                MessageUtil.sendMessage(player, "&a已将 &bALL &a权限设置为 &e仅岛主 &a拥有");
            } else {
                MessageUtil.sendMessage(player,
                        "&a已将 &bALL &a权限的最低等级设置为 &e" + targetRole.getDisplayName() +
                                " &a(&6" + targetLevel + "&e) 及以上角色拥有（包括岛主）");
            }
        } else {
            MessageUtil.sendMessage(player,
                    "&a已将权限 &b" + permissionName +
                            " &a的最低等级设置为 &e" + targetRole.getDisplayName() +
                            " &a(&6" + targetLevel + "&e) 及以上角色拥有");
        }
    }

    public boolean handlePermissionsListCommand(Player player) {
        MessageUtil.sendMessage(player, "&a=== 所有可用权限列表 ===");

        MessageUtil.sendMessage(player, "&e=== 岛屿管理权限 ===");
        MessageUtil.sendMessage(player, "&7ALL, DELETE_ISLAND, CHANGE_NAME, SET_PERMISSIONS, SET_HOME, SET_BIOME");

        MessageUtil.sendMessage(player, "&e=== 成员管理权限 ===");
        MessageUtil.sendMessage(player, "&7INVITE_MEMBER, REMOVE_MEMBER, SET_ROLE, INVITE_COOP, REMOVE_COOP");

        MessageUtil.sendMessage(player, "&e=== 基础交互权限 ===");
        MessageUtil.sendMessage(player, "&7DROP_ITEMS, PICKUP_DROPS, EXP_BALL, BREAK, BUILD, INTERACT");

        MessageUtil.sendMessage(player, "&e=== 工作方块权限 ===");
        MessageUtil.sendMessage(player,
                "&7WORKBLOCK_ACCESS, WORKBENCH, ENCHANTING_TABLES, BEACON, ANVIL, GRINDSTONE, CARTOGRAPHY_TABLE, STONECUTTER, LOOM, SMITHING_TABLE, CAMPFIRE, NOTE_BLOCK");

        MessageUtil.sendMessage(player, "&e=== 容器权限 ===");
        MessageUtil.sendMessage(player,
                "&7CONTAINER_ACCESS, FURNACE, CHEST, BUCKET, ENDER_CHEST, SHULKER_BOX, HOPPER, DISPENSER, DROPER, CRAFTING, BREWING_STAND, CAULDRON, SHEIF, ITEM_FRAME, JUKEBOX, LECTERN, CHISELED_BOOKSHELF, DECORATED_POT, COMPOSTER, RESPAWN_ANCHOR");

        MessageUtil.sendMessage(player, "&e=== 红石权限 ===");
        MessageUtil.sendMessage(player,
                "&7REDSTONE_ACCESS, BUTTON, LEVER, PRESSURE_PLATE, REDSTONE_REPEATER, REDSTONE_COMPARATOR, DAYLIGHT_DETECTOR, SCULK_SENSOR, BELL");

        MessageUtil.sendMessage(player, "&e=== 门权限 ===");
        MessageUtil.sendMessage(player, "&7DOOR_ACCESS, DOOR, FENCE_GATE, TRAPDOOR");

        MessageUtil.sendMessage(player, "&e=== 交通工具权限 ===");
        MessageUtil.sendMessage(player,
                "&7MINECART_DAMAGE, MINECART_ENTER, MINECART_PLACE, SHIP_DAMAGE, SHIP_ENTER, SHIP_PLACE");

        MessageUtil.sendMessage(player, "&e=== 物品使用权限 ===");
        MessageUtil.sendMessage(player,
                "&7FIREWORK, POTION, CHORUS_FRUIT, ENDER_PEARL, BRUSH, LEASH, FISH, PICKUP_FISH, SIGN_INTERACT, NAME, FIRE, WIND_CHARGE, DROP_SNOWBALL, DROP_EGG, SADDLE_ENTITY, ENTITY_RIDE");

        MessageUtil.sendMessage(player, "&e=== 农业权限 ===");
        MessageUtil.sendMessage(player, "&7FARM_TRAMPING, TURTLE_EGG_TRAMPING, FARMING, FERTILIZE");

        MessageUtil.sendMessage(player, "&e=== 生物交互权限 ===");
        MessageUtil.sendMessage(player,
                "&7ANIMAL_SPAWN, ANIMAL_FEEDING, ANIMAL_DAMAGE, ANIMAL_SHEAR, DYE_SHEEP, MILK, MUSHROOM_BOWL, MUSHROOM_CUT, VILLAGER_TRADING, MONSTER_SPAWN, MONSTER_DAMAGE");

        MessageUtil.sendMessage(player, "&a=== 使用示例 ===");
        MessageUtil.sendMessage(player, "&7/is permission ALL 4 → ADMIN(4) 及以上（含岛主）拥有全部权限");
        MessageUtil.sendMessage(player, "&7/is permission ALL 5 → 仅岛主拥有全部权限");
        MessageUtil.sendMessage(player, "&7/is permission BUILD 2 → MEMBER(2) 及以上拥有建造权限");

        return true;
    }

    public List<String> onTabComplete(String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("permission")) {
            String prefix = args[1].toUpperCase();
            return Arrays.stream(IslandPermission.values())
                    .map(Enum::name)
                    .filter(name -> name.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("permission")) {
            String prefix = args[2].toUpperCase();
            List<String> completions = new ArrayList<>();

            for (IslandPermissionLevel role : IslandPermissionLevel.values()) {
                if (role.name().startsWith(prefix)) {
                    completions.add(role.name());
                }
            }
            for (int i = 0; i <= 5; i++) {
                String level = String.valueOf(i);
                if (level.startsWith(prefix)) {
                    completions.add(level);
                }
            }
            return completions;
        }

        return null;
    }
}
