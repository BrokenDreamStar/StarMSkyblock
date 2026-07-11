package team.starm.starmskyblock.permission;

import team.starm.starmskyblock.message.MessageUtil;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * 岛屿权限等级（身份组）枚举
 * <p>
 * 定义了岛屿成员的身份等级体系，从高到低依次为：
 * 岛主(OWNER) &gt; 管理员(ADMIN) &gt; 风纪委员(MOD) &gt; 岛员(MEMBER) &gt; 合作者(COOP) &gt; 访客(VISITOR)。
 * 每个等级拥有一个数值化的权限级别，用于比较大小和权限继承。
 * </p>
 * <p>
 * 显示名（{@link #getDisplayName()}）通过 i18n 消息键 {@code role.<name>} 解析，
 * 见 {@code messages/zh_CN.yml}。{@link #fromString(String)} 仍通过 {@link #getDisplayName()}
 * 反查中文，因此中文名与枚举名均可解析（行为与迁移前一致）。
 * </p>
 */
public enum IslandPermissionLevel {
    OWNER(5, "&6"),
    ADMIN(4, "&c"),
    MOD(3, "&2"),
    MEMBER(2, "&a"),
    COOP(1, "&b"),
    VISITOR(0, "&f");

    private final int permissionLevel;
    private final String color;

    IslandPermissionLevel(int permissionLevel, String color) {
        this.permissionLevel = permissionLevel;
        this.color = color;
    }

    public String getDisplayName() {
        return MessageUtil.format("role." + name().toLowerCase(Locale.ROOT));
    }

    public int getPermissionLevel() {
        return permissionLevel;
    }

    public String getColor() {
        return color;
    }

    /**
     * 从字符串解析岛屿等级
     * <p>
     * 支持通过枚举名称（不区分大小写）或本地化显示名称匹配。
     * 用于从 YAML 配置文件和外部输入中解析权限组等级，
     * 例如解析 permissions.yml 中的权限组配置或指令参数。
     *
     * @param roleName 权限组名称字符串
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
     * 获取当前权限组可以管理的所有下级权限组集合
     * <p>
     * 用于权限界面和指令中的防越权保护：低等级权限组不能修改高等级权限组的权限。
     *
     * @param currentRole 当前操作者的权限组
     * @return 当前权限组可管理的下级权限组集合（不含同级和更高级别）
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

    /**
     * 基于权限组与该权限配置的最低等级判定是否拥有权限。
     * <p>
     * 岛主(OWNER)永远拥有全部权限；其他权限组需达到该权限在岛屿中配置的最低等级，
     * 未配置（null）则拒绝。
     *
     * @param permission         待检查的权限
     * @param configuredMinLevel 该权限在此岛屿配置的最低等级（null 表示未配置）
     * @return 是否拥有该权限
     */
    public boolean hasPermission(IslandPermission permission, Integer configuredMinLevel) {
        if (this == OWNER) {
            return true;
        }
        if (configuredMinLevel == null) {
            return false;
        }
        return permissionLevel >= configuredMinLevel;
    }
}
