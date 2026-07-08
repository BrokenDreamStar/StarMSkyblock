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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * /is permission 子命令的处理器。
 * 负责查看和修改每个权限（如 BUILD、BREAK、INVITE_MEMBER 等）所需的最低权限组等级。
 * 支持通过权限组名（OWNER/ADMIN/MOD/MEMBER/COOP/VISITOR）、数字等级（0-5）
 * 以及 cycle/rcycle 循环模式进行设置。
 * 内嵌 PermissionCategory 枚举对权限进行类别分组，用于显示帮助列表。
 */
public class IslandPermissionCommand {

    private final StarMSkyblock plugin;

    public IslandPermissionCommand(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * /is permission 的主入口。
     * 参数不足时显示用法，2 个参数时查询权限配置，3+ 参数时修改最低等级。
     */
    public boolean handlePermissionCommand(Player player, String[] args) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();

        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.EDIT_PERMISSIONS)) {
            MessageUtil.send(player, "island.permission.no-permission");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.send(player, "island.permission.usage");
            MessageUtil.send(player, "island.permission.example-basic");
            MessageUtil.send(player, "island.permission.example-cycle");
            MessageUtil.send(player, "island.permission.example-rcycle");
            MessageUtil.send(player, "island.permission.example-query");
            MessageUtil.send(player, "island.permission.management-restriction");
            return true;
        }

        String permissionInput = args[1].toLowerCase();

        if (args.length > 3) {
            MessageUtil.send(player, "island.permission.usage");
            return true;
        }

        if (args.length == 2) {
            return showPermissionConfig(player, island, permissionInput);
        }

        return setPermissionLevel(player, island, args, permissionInput);
    }

    /**
     * 将字符串匹配为 IslandPermission 枚举值，不区分大小写。
     */
    private IslandPermission resolvePermission(String input) {
        for (IslandPermission perm : IslandPermission.values()) {
            if (perm.name().equalsIgnoreCase(input)) {
                return perm;
            }
        }
        return null;
    }

    private boolean showPermissionConfig(Player player, Island island, String permissionInput) {
        IslandPermission permission = resolvePermission(permissionInput);
        if (permission == null) {
            MessageUtil.send(player, "island.permission.invalid", Map.of("permission", permissionInput));
            return true;
        }

        MessageUtil.send(player, "island.permission.config-header", Map.of("permission", permission.name().toLowerCase()));

        for (IslandPermissionLevel role : IslandPermissionLevel.values()) {
            if (island.hasPermission(role, permission)) {
                MessageUtil.send(player, "island.permission.config-entry-has",
                        Map.of("role", role.getDisplayName(), "level", role.getPermissionLevel()));
            } else {
                MessageUtil.send(player, "island.permission.config-entry-none",
                        Map.of("role", role.getDisplayName(), "level", role.getPermissionLevel()));
            }
        }
        return true;
    }

    private boolean setPermissionLevel(Player player, Island island, String[] args, String permissionInput) {
        String roleInput = args[2];

        IslandPermission permission = resolvePermission(permissionInput);
        if (permission == null) {
            MessageUtil.send(player, "island.permission.invalid", Map.of("permission", permissionInput));
            return true;
        }

        // 循环模式
        if (roleInput.equalsIgnoreCase("cycle")) {
            return cyclePermissionLevel(player, island, permission, permissionInput, true);
        }
        if (roleInput.equalsIgnoreCase("rcycle")) {
            return cyclePermissionLevel(player, island, permission, permissionInput, false);
        }

        IslandPermissionLevel targetRole = parseTargetRole(roleInput);
        if (targetRole == null) {
            MessageUtil.send(player, "island.permission.invalid-role", Map.of("role", roleInput));
            MessageUtil.send(player, "island.permission.available-roles");
            return true;
        }

        IslandPermissionLevel playerRole = island.getMemberRole(player.getUniqueId());

        // 检查玩家是否有权限管理目标权限组的权限
        if (!IslandPermissionLevel.getManageableRoles(playerRole).contains(targetRole)) {
            MessageUtil.send(player, "island.permission.cannot-manage", Map.of("role", targetRole.getDisplayName()));
            return true;
        }

        int targetLevel = targetRole.getPermissionLevel();

        // 管理权限仅允许分配给风纪委员(3)及以上权限组
        if (permission.isManagement() && targetLevel < 3) {
            MessageUtil.send(player, "island.permission.management-level-too-low", Map.of("role", targetRole.getDisplayName()));
            return true;
        }
        IslandManager islandManager = plugin.getIslandManager();

        islandManager.setPermissionMinLevel(island.getId(), permission, targetLevel);

        sendPermissionUpdateMessage(player, permission, permissionInput, targetRole, targetLevel);
        return true;
    }

    /**
     * cycle / rcycle 模式：在当前等级基础上 +1（或 -1）循环。
     * 普通权限范围 0-5，管理权限限制在 3-5 之间循环。
     *
     * @param forward true 为递增 cycle，false 为递减 rcycle
     */
    private boolean cyclePermissionLevel(Player player, Island island, IslandPermission permission,
                                         String permissionInput, boolean forward) {
        IslandManager islandManager = plugin.getIslandManager();

        Integer currentLevel = island.getPermissionMinLevel(permission);
        if (currentLevel == null) {
            currentLevel = IslandPermissionLevel.OWNER.getPermissionLevel();
        }

        int nextLevel;
        if (permission.isManagement()) {
            // 管理权限仅在 3(MOD) / 4(ADMIN) / 5(OWNER) 之间循环
            if (forward) {
                nextLevel = currentLevel >= 5 ? 3 : currentLevel + 1;
            } else {
                nextLevel = currentLevel <= 3 ? 5 : currentLevel - 1;
            }
        } else {
            nextLevel = forward ? (currentLevel + 1) % 6 : (currentLevel + 5) % 6;
        }

        IslandPermissionLevel targetRole = null;
        for (IslandPermissionLevel r : IslandPermissionLevel.values()) {
            if (r.getPermissionLevel() == nextLevel) {
                targetRole = r;
                break;
            }
        }

        islandManager.setPermissionMinLevel(island.getId(), permission, nextLevel);

        sendPermissionUpdateMessage(player, permission, permissionInput, targetRole, nextLevel);
        return true;
    }

    /**
     * 将输入解析为目标权限组等级。支持数字 0-5 或权限组枚举名。
     */
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
                                             String permissionInput, IslandPermissionLevel targetRole, int targetLevel) {
        MessageUtil.send(player, "island.permission.update-success",
                Map.of("permission", permissionInput, "role", targetRole.getDisplayName(), "level", targetLevel));
    }

    public boolean handlePermissionsListCommand(Player player) {
        MessageUtil.send(player, "island.permission.list-header");

        // 动态按类别分组显示权限
        for (PermissionCategory category : PermissionCategory.values()) {
            var perms = category.getPermissions();
            if (perms.isEmpty()) continue;

            MessageUtil.send(player, "island.permission.category-header", Map.of("category", category.displayName));
            MessageUtil.send(player, "island.permission.category-entries", Map.of("permissions",
                    String.join(", ", perms.stream().map(String::toLowerCase).toList())));
        }

        MessageUtil.send(player, "island.permission.examples-header");
        MessageUtil.send(player, "island.permission.example-all-4");
        MessageUtil.send(player, "island.permission.example-all-5");
        MessageUtil.send(player, "island.permission.example-build-2");
        MessageUtil.send(player, "island.permission.management-restriction");

        return true;
    }

    private enum PermissionCategory {
        MANAGEMENT("管理权限", s -> s.startsWith("RENAME_") || s.startsWith("EDIT_")
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
            this.displayName = displayName;
            this.extra = new String[0];
            this.matcher = matcher;
        }

        boolean matches(String name) {
            return matcher.test(name);
        }

        List<String> getPermissions() {
            List<String> result = new ArrayList<>();
            result.addAll(List.of(extra));
            for (IslandPermission perm : IslandPermission.values()) {
                String name = perm.name();
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
            String prefix = args[1].toLowerCase();
            return Arrays.stream(IslandPermission.values())
                    .map(perm -> perm.name().toLowerCase())
                    .filter(name -> name.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("permission")) {
            String prefix = args[2].toLowerCase();
            List<String> completions = new ArrayList<>();

            if ("cycle".startsWith(prefix)) {
                completions.add("CYCLE");
            }
            if ("rcycle".startsWith(prefix)) {
                completions.add("RCYCLE");
            }

            // 判断是否为管理权限，管理权限仅允许补全 MOD/ADMIN/OWNER
            IslandPermission perm = resolvePermission(args[1]);
            boolean isManagementPerm = perm != null && perm.isManagement();

            for (IslandPermissionLevel role : IslandPermissionLevel.values()) {
                if ((!isManagementPerm || role.getPermissionLevel() >= 3)
                        && role.name().toLowerCase().startsWith(prefix)) {
                    completions.add(role.name());
                }
            }
            if (isManagementPerm) {
                for (int i = 3; i <= 5; i++) {
                    String level = String.valueOf(i);
                    if (level.startsWith(prefix)) {
                        completions.add(level);
                    }
                }
            } else {
                for (int i = 0; i <= 5; i++) {
                    String level = String.valueOf(i);
                    if (level.startsWith(prefix)) {
                        completions.add(level);
                    }
                }
            }
            return completions;
        }

        return null;
    }
}
