package team.starm.starmskyblock.permission;

import java.util.EnumSet;
import java.util.Set;

public enum IslandPermissionLevel {
    OWNER("岛主", 5, "拥有岛屿的完全控制权"),
    ADMIN("管理员", 4, "可以管理成员和大部分岛屿设置"),
    MOD("风纪委员", 3, "可以管理部分岛屿设置"),
    MEMBER("岛员", 2, "普通成员，拥有基本权限"),
    COOP("合作者", 1, "临时合作者，权限有限"),
    VISITOR("访客", 0, "只读访问权限");

    private final String displayName;
    private final int permissionLevel;
    private final String description;

    IslandPermissionLevel(String displayName, int permissionLevel, String description) {
        this.displayName = displayName;
        this.permissionLevel = permissionLevel;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getPermissionLevel() {
        return permissionLevel;
    }

    public String getDescription() {
        return description;
    }

    // 保留：用于从配置文件(YAML)解析等级字符串
    public static IslandPermissionLevel fromString(String roleName) {
        for (IslandPermissionLevel role : values()) {
            if (role.name().equalsIgnoreCase(roleName) || role.getDisplayName().equalsIgnoreCase(roleName)) {
                return role;
            }
        }
        return VISITOR;
    }

    // 保留：用于指令/UI中的防越权保护（低级别不能修改高级别的权限）
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
