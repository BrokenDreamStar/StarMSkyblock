package team.starm.starmskyblock.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionLevel;

import java.io.File;
import java.io.IOException;
import java.util.*;

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

        plugin.getLogger().info("§a[权限系统] 已加载默认权限配置（支持继承模式）");
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
                plugin.getLogger().warning("§e无效继承配置: " + level.name() + " 继承 " + parentName + "（已忽略）");
            }
        }

        // 2. 添加本组额外权限
        String addKey = level.name() + ".additional";
        List<String> additionalList = permissionsConfig.getStringList(addKey);
        parsePermissionList(additionalList).forEach(perms::add);

        // 3. 排除权限
        String exKey = level.name() + ".excluded";
        List<String> excludedList = permissionsConfig.getStringList(exKey);
        parsePermissionList(excludedList).forEach(perms::remove);

        // 4. OWNER 强制全权限
        if (level == IslandPermissionLevel.OWNER) {
            perms.clear();
            perms.addAll(Arrays.asList(IslandPermission.values()));
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
                if (perm == IslandPermission.ALL) {
                    set.addAll(Arrays.asList(IslandPermission.values()));
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("§e未知的权限: " + name + "（已跳过）");
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
        if (perms.contains(IslandPermission.ALL))
            return true;
        return perms.contains(permission);
    }

    public void savePermissionsConfig() {
        if (permissionsConfig != null && permissionsFile != null) {
            try {
                permissionsConfig.save(permissionsFile);
            } catch (IOException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "保存权限配置文件失败", e);
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