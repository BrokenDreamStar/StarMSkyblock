package team.starm.starmskyblock.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionLevel;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限配置文件（permissions.yml）管理器。
 * 采用继承式权限模型：每个权限组可以指定从哪个父权限组继承权限，
 * 再额外添加（additional）和排除（excluded）特定权限。
 * OWNER 权限组强制拥有所有权限。支持 ALL 通配权限。
 * 首次启动时自动从 jar 包释放默认 permissions.yml。
 */
public class PermissionConfigManager {

    private final StarMSkyblock plugin;
    private FileConfiguration permissionsConfig;
    private File permissionsFile;

    // 每个权限级别的最终权限集合
    private final Map<IslandPermissionLevel, Set<IslandPermission>> defaultPermissions = new EnumMap<>(
            IslandPermissionLevel.class);

    public PermissionConfigManager(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        loadPermissionsConfig();
    }

    public void loadPermissionsConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        permissionsFile = new File(plugin.getDataFolder(), "permissions.yml");

        if (!permissionsFile.exists()) {
            plugin.saveResource("permissions.yml", false);
        }

        permissionsConfig = YamlConfiguration.loadConfiguration(permissionsFile);

        loadDefaultPermissions();
        // 注意：我们不再需要 deny-messages 配置，消息模板已硬编码在代码中
    }

    /**
     * 新版继承式权限加载（支持任意层级继承）
     */
    private void loadDefaultPermissions() {
        defaultPermissions.clear();
        Map<IslandPermissionLevel, Set<IslandPermission>> cache = new EnumMap<>(IslandPermissionLevel.class);

        for (IslandPermissionLevel level : IslandPermissionLevel.values()) {
            Set<IslandPermission> perms = resolvePermissions(level, cache);
            defaultPermissions.put(level, perms);
        }

        MessageUtil.consolePrint("已加载岛屿默认权限配置");
    }

    /**
     * 递归解析权限（带缓存 + 循环保护）
     */
    private Set<IslandPermission> resolvePermissions(IslandPermissionLevel level,
                                                     Map<IslandPermissionLevel, Set<IslandPermission>> cache) {
        if (cache.containsKey(level)) {
            return cache.get(level);
        }

        Set<IslandPermission> perms = new HashSet<>();

        // 1. 处理继承
        String inheritsKey = level.name() + ".inherits-from";
        String parentName = permissionsConfig.getString(inheritsKey, "").trim();

        if (!parentName.isEmpty() && !"none".equalsIgnoreCase(parentName)) {
            IslandPermissionLevel parent = IslandPermissionLevel.fromString(parentName);
            if (parent != null && parent != level) {
                perms.addAll(resolvePermissions(parent, cache));
            } else {
                MessageUtil.consoleWarn("无效继承配置: " + level.name() + " 继承 " + parentName + "（已忽略）");
            }
        }

        // 2. 添加本组额外权限
        String addKey = level.name() + ".additional";
        List<String> additionalList = permissionsConfig.getStringList(addKey);
        perms.addAll(parsePermissionList(additionalList));

        // 3. 排除权限
        String exKey = level.name() + ".excluded";
        List<String> excludedList = permissionsConfig.getStringList(exKey);
        parsePermissionList(excludedList).forEach(perms::remove);

        // 4. OWNER 强制全权限
        if (level == IslandPermissionLevel.OWNER) {
            perms.clear();
            perms.addAll(EnumSet.allOf(IslandPermission.class));
        }

        cache.put(level, perms);
        return perms;
    }

    /**
     * 把 String List 转成 IslandPermission Set（支持 ALL）
     */
    private Set<IslandPermission> parsePermissionList(List<String> list) {
        Set<IslandPermission> set = new HashSet<>();
        for (String name : list) {
            if (name == null || name.trim().isEmpty())
                continue;
            try {
                IslandPermission perm = IslandPermission.valueOf(name.trim());
                set.add(perm);
            } catch (IllegalArgumentException e) {
                MessageUtil.consoleWarn("未知的权限: " + name + "（已跳过）");
            }
        }
        return set;
    }

    public Set<IslandPermission> getDefaultPermissions(IslandPermissionLevel level) {
        return new HashSet<>(defaultPermissions.getOrDefault(level, Collections.emptySet()));
    }

    public boolean hasDefaultPermission(IslandPermissionLevel level, IslandPermission permission) {
        Set<IslandPermission> perms = defaultPermissions.get(level);
        if (perms == null)
            return false;
        return perms.contains(permission);
    }

    /**
     * 计算每个权限的初始最低等级（岛屿创建时使用）
     * 遍历权限组从低到高，找到能拥有该权限的最低权限组等级
     */
    public Map<IslandPermission, Integer> getDefaultMinLevels() {
        Map<IslandPermission, Integer> levels = new HashMap<>();

        IslandPermissionLevel[] ascending = {
                IslandPermissionLevel.VISITOR,
                IslandPermissionLevel.COOP,
                IslandPermissionLevel.MEMBER,
                IslandPermissionLevel.MOD,
                IslandPermissionLevel.ADMIN,
                IslandPermissionLevel.OWNER
        };

        for (IslandPermission permission : IslandPermission.values()) {
            for (IslandPermissionLevel role : ascending) {
                if (hasDefaultPermission(role, permission)) {
                    levels.put(permission, role.getPermissionLevel());
                    break;
                }
            }
        }

        return levels;
    }

    public void savePermissionsConfig() {
        if (permissionsConfig != null && permissionsFile != null) {
            try {
                permissionsConfig.save(permissionsFile);
            } catch (IOException e) {
                MessageUtil.consoleError("保存权限配置文件失败");
                e.printStackTrace();
            }
        }
    }

    public void reloadPermissionsConfig() {
        loadPermissionsConfig();
    }

    public FileConfiguration getPermissionsConfig() {
        return permissionsConfig;
    }
}
