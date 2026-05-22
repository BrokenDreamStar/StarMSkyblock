package team.starm.starmskyblock.permission;

import java.util.EnumSet;
import java.util.Set;

/**
 * 岛屿权限等级（身份组）枚举
 * <p>
 * 定义了岛屿成员的身份等级体系，从高到低依次为：
 * 岛主(OWNER) &gt; 管理员(ADMIN) &gt; 风纪委员(MOD) &gt; 岛员(MEMBER) &gt; 合作者(COOP) &gt; 访客(VISITOR)。
 * 每个等级拥有一个数值化的权限级别，用于比较大小和权限继承。
 * </p>
 */
public enum IslandPermissionLevel {
    OWNER("岛主", 5),
    ADMIN("管理员", 4),
    MOD("风纪委员", 3),
    MEMBER("岛员", 2),
    COOP("合作者", 1),
    VISITOR("访客", 0);

    private final String displayName;
    private final int permissionLevel;

    IslandPermissionLevel(String displayName, int permissionLevel) {
        this.displayName = displayName;
        this.permissionLevel = permissionLevel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getPermissionLevel() {
        return permissionLevel;
    }

    /**
     * 从字符串解析岛屿等级
     * <p>
     * 支持通过枚举名称（不区分大小写）或中文显示名称匹配。
     * 用于从 YAML 配置文件和外部输入中解析角色等级，
     * 例如解析 permissions.yml 中的角色配置或指令参数。
     * </p>
     *
     * @param roleName 角色名称字符串
     * @return 匹配的等级，未匹配时返回 VISITOR
     */
    public static IslandPermissionLevel fromString(String roleName) {
        for (IslandPermissionLevel role : values()) {
            if (role.name().equalsIgnoreCase(roleName) || role.getDisplayName().equalsIgnoreCase(roleName)) {
                return role;
            }
        }
        return VISITOR;
    }

    /**
     * 获取当前角色可以管理的所有下级角色集合
     * <p>
     * 用于权限界面和指令中的防越权保护：低等级角色不能修改高等级角色的权限。
     * 例如岛主可以管理所有人，管理员不能管理其他管理员和岛主，风纪委员只能管理普通岛员及以下。
     * 这确保了权限层级的安全约束。
     * </p>
     *
     * @param currentRole 当前操作者的角色
     * @return 当前角色可管理的下级角色集合（不含同级和更高级别）
     */
    public static Set<IslandPermissionLevel> getManageableRoles(IslandPermissionLevel currentRole) {
        Set<IslandPermissionLevel> manageable = EnumSet.noneOf(IslandPermissionLevel.class);
        switch (currentRole) {
            case OWNER:
                manageable.addAll(EnumSet.allOf(IslandPermissionLevel.class));
                break;
            case ADMIN:
                manageable.add(MOD);
                manageable.add(MEMBER);
                manageable.add(COOP);
                manageable.add(VISITOR);
                break;
            case MOD:
                manageable.add(MEMBER);
                manageable.add(COOP);
                manageable.add(VISITOR);
                break;
            default:
                break;
        }
        return manageable;
    }
}
