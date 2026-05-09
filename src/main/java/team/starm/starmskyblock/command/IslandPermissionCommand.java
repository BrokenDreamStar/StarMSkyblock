package team.starm.starmskyblock.command;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
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
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();

        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.EDIT_PERMISSIONS)) {
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
            MessageUtil.sendMessage(player, "&c输入 /is permission 按空格+TAB 查看所有可用权限");
            return true;
        }

        MessageUtil.sendMessage(player, "&a=== 权限 " + permissionName + " 当前配置 ===");

        boolean hasCustom = false;
        for (IslandPermissionLevel role : IslandPermissionLevel.values()) {
            String status;
            if (island.hasPermission(role, permission)) {
                status = "&a拥有";
            } else {
                status = "&c无";
            }
            MessageUtil.sendMessage(player,
                    "&e" + role.getDisplayName() + "(&6" + role.getPermissionLevel() + "&e): " + status);
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
        if (!IslandPermissionLevel.getManageableRoles(playerRole).contains(targetRole)) {
            MessageUtil.sendMessage(player, "&c你不能管理 " + targetRole.getDisplayName() + " 角色的权限！");
            return true;
        }

        int targetLevel = targetRole.getPermissionLevel();
        IslandManager islandManager = plugin.getIslandManager();

        islandManager.setPermissionMinLevel(island.getId(), permission, targetLevel);

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
        MessageUtil.sendMessage(player, "&7（使用 Tab 补全查看 /is permission <权限名>）");

        // 动态按类别分组显示权限
        for (PermissionCategory category : PermissionCategory.values()) {
            var perms = category.getPermissions();
            if (perms.isEmpty()) continue;

            MessageUtil.sendMessage(player, "&e=== " + category.displayName + " ===");
            MessageUtil.sendMessage(player, "&7" + String.join(", ", perms));
        }

        MessageUtil.sendMessage(player, "&a=== 使用示例 ===");
        MessageUtil.sendMessage(player, "&7/is permission ALL 4 → ADMIN(4) 及以上（含岛主）拥有全部权限");
        MessageUtil.sendMessage(player, "&7/is permission ALL 5 → 仅岛主拥有全部权限");
        MessageUtil.sendMessage(player, "&7/is permission BUILD 2 → MEMBER(2) 及以上拥有建造权限");

        return true;
    }

    private enum PermissionCategory {
        MANAGEMENT("管理权限", "ALL", s -> s.startsWith("DELETE_") || s.startsWith("RENAME_") || s.startsWith("EDIT_")
                || s.startsWith("INVITE_") || s.startsWith("REMOVE_") || s.startsWith("SET_")),
        ITEM_DROP_PICKUP("丢弃/拾取", s -> s.equals("ITEM_DROP") || s.equals("ITEM_PICKUP") || s.equals("EXP_PICKUP")),
        BLOCK("方块破坏/建造", s -> s.equals("BREAK") || s.equals("BUILD")),
        WORKBLOCK("工作方块", s -> s.endsWith("_USE") && !isToolOrItem(s)),
        CONTAINER("容器", s -> s.endsWith("_OPEN") || s.equals("SHELF_USE") || s.equals("ITEM_FRAME_USE") || s.equals("JUKEBOX_USE")
                || s.equals("LECTERN_USE") || s.equals("CHISELED_BOOKSHELF_USE") || s.equals("DECORATED_POT_USE")
                || s.equals("COMPOSTER_USE") || s.equals("FLOWER_POT_USE") || s.equals("ANIMAL_INVENTORY_OPEN")),
        REDSTONE("红石", s -> s.equals("BUTTON_PRESS") || s.equals("LEVER_USE") || s.startsWith("REPEATER_")
                || s.startsWith("COMPARATOR_") || s.startsWith("DAYLIGHT_") || s.startsWith("PRESSURE_")
                || s.startsWith("TRIPWIRE_") || s.startsWith("SCULK_") || s.equals("BELL_RING")),
        DOOR("门", s -> s.equals("DOOR_OPEN") || s.equals("FENCE_GATE_OPEN") || s.equals("TRAPDOOR_OPEN")),
        VEHICLE("载具", s -> s.startsWith("MINECART_") || s.startsWith("BOAT_")),
        TOOL("工具", s -> isTool(s)),
        ITEM("物品", s -> isItem(s)),
        ENTITY("生物", s -> isEntityPermission(s)),
        OTHER("其它", s -> !MANAGEMENT.matches(s) && !ITEM_DROP_PICKUP.matches(s) && !BLOCK.matches(s)
                && !WORKBLOCK.matches(s) && !CONTAINER.matches(s) && !REDSTONE.matches(s)
                && !DOOR.matches(s) && !VEHICLE.matches(s) && !TOOL.matches(s) && !ITEM.matches(s)
                && !ENTITY.matches(s));

        private final String displayName;
        private final java.util.function.Predicate<String> matcher;
        private final String[] extra;

        PermissionCategory(String displayName, java.util.function.Predicate<String> matcher) {
            this(displayName, null, matcher);
        }

        PermissionCategory(String displayName, String extra, java.util.function.Predicate<String> matcher) {
            this.displayName = displayName;
            this.extra = extra != null ? new String[]{extra} : new String[0];
            this.matcher = matcher;
        }

        boolean matches(String name) {
            return matcher.test(name);
        }

        List<String> getPermissions() {
            List<String> result = new ArrayList<>();
            for (String extra : extra) {
                result.add(extra);
            }
            for (IslandPermission perm : IslandPermission.values()) {
                String name = perm.name();
                if (name.equals("ALL")) continue;
                if (matches(name) && !result.contains(name)) {
                    result.add(name);
                }
            }
            return result;
        }

        private static boolean isToolOrItem(String name) {
            return isTool(name) || isItem(name);
        }

        private static boolean isTool(String name) {
            return switch (name) {
                case "BOW_USE", "AXE_USE", "SHOVEL_USE", "HOE_USE", "BUCKET_USE",
                     "GLASS_BOTTLE_USE", "BOWL_USE", "FISHING_ROD_USE",
                     "FLINT_AND_STEEL_USE", "SHEARS_USE", "BRUSH_USE", "LEASH_USE" -> true;
                default -> false;
            };
        }

        private static boolean isItem(String name) {
            return switch (name) {
                case "FIREWORK_USE", "NAME_TAG_USE", "POTION_THROW", "WATER_BOTTLE_USE",
                     "BONE_MEAL_USE", "DYE_USE", "INK_SAC_USE", "HONEYCOMB_USE",
                     "CHORUS_FRUIT_EAT", "ENDER_PEARL_USE", "ENDER_EYE_USE",
                     "WIND_CHARGE_USE", "SNOWBALL_THROW", "EGG_THROW" -> true;
                default -> false;
            };
        }

        private static boolean isEntityPermission(String name) {
            return switch (name) {
                case "ANIMAL_FEED", "ENTITY_RIDE", "ENTITY_EQUIP", "ANIMAL_DAMAGE",
                     "MONSTER_DAMAGE", "VILLAGER_DAMAGE", "VILLAGER_TRADE",
                     "BARTERING", "ALLAY_INTERACT", "ARMOR_STAND_DAMAGE",
                     "ARMOR_STAND_INTERACT" -> true;
                default -> false;
            };
        }
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
