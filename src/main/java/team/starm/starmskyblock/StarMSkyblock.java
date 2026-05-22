package team.starm.starmskyblock;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import team.starm.starmskyblock.command.AdminCommand;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.PermissionConfigManager;
import team.starm.starmskyblock.database.SQLiteManager;
import team.starm.starmskyblock.generator.SchematicManager;
import team.starm.starmskyblock.grid.GridManager;
import team.starm.starmskyblock.island.InvitationManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.listener.BorderListener;
import team.starm.starmskyblock.listener.PortalListener;
import team.starm.starmskyblock.setting.IslandSettingManager;
import team.starm.starmskyblock.config.SettingsConfigManager;
import team.starm.starmskyblock.config.SignConfigManager;
import team.starm.starmskyblock.permission.IslandPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.world.SkyblockWorldManager;

/**
 * StarMSkyblock 插件的主入口类。
 * 继承 JavaPlugin，负责在服务器启动时初始化所有子系统：
 * 配置管理器、数据库、结构加载器、网格系统、岛屿管理器、世界管理器、
 * 邀请系统、权限协调器、监听器以及 PlaceholderAPI 扩展。
 * 也是各个管理器单例的集中持有者（通过 getInstance() 访问）。
 */
public class StarMSkyblock extends JavaPlugin {

    /** 插件单例实例 */
    private static StarMSkyblock instance;

    // ========== 配置管理器 ==========
    private ConfigManager configManager;               // 主配置（config.yml）
    private PermissionConfigManager permissionConfigManager; // 权限配置（permissions.yml）
    private SettingsConfigManager settingsConfigManager;     // 岛屿默认设置（settings.yml）
    private SignConfigManager signConfigManager;             // 告示牌文字配置（sign.yml）

    // ========== 数据与生成 ==========
    private SQLiteManager sqliteManager;               // SQLite 数据库管理器
    private SchematicManager schematicManager;         // WorldEdit 结构文件加载器
    private GridManager gridManager;                   // 岛屿网格坐标系统
    private IslandManager islandManager;               // 岛屿核心业务逻辑

    // ========== 功能模块 ==========
    private InvitationManager invitationManager;       // 玩家邀请系统
    private SkyblockWorldManager worldManager;         // 虚空世界创建/加载
    private IslandPermissionManager permissionCoordinator; // 岛屿权限协调器
    private BorderListener borderListener;             // 岛屿边界显示监听器

    /**
     * 插件启用入口。依次初始化所有子系统，确保依赖关系正确：
     * 配置 -> 数据库 -> 结构管理器 -> 网格/岛屿 -> 世界 -> 邀请 & 权限 -> 监听器 -> 命令。
     */
    @Override
    public void onEnable() {
        instance = this;

        // 初始化配置
        configManager = new ConfigManager(this);
        configManager.initialize();

        // 初始化权限配置
        permissionConfigManager = new PermissionConfigManager(this);
        permissionConfigManager.initialize();

        // 初始化岛屿默认设置配置
        settingsConfigManager = new SettingsConfigManager(this);
        settingsConfigManager.initialize();

        // 初始化告示牌配置
        signConfigManager = new SignConfigManager(this);
        signConfigManager.initialize();

        // 释放内置的schematics文件
        extractSchematics();

        // 初始化数据库
        sqliteManager = new SQLiteManager(getDataFolder());
        sqliteManager.init();

        // 初始化 FAWE/WorldEdit 结构管理器
        schematicManager = new SchematicManager(new File(getDataFolder(), "schematics"));

        // 初始化网格系统和岛屿管理器
        gridManager = new GridManager(configManager);
        islandManager = new IslandManager(configManager, permissionConfigManager, settingsConfigManager,
                gridManager, sqliteManager);

        // 初始化世界管理器
        worldManager = new SkyblockWorldManager(configManager);

        invitationManager = new InvitationManager(islandManager, configManager, worldManager);

        // 每5分钟清理到期邀请（5分钟 = 6000 tick）
        getServer().getScheduler().runTaskTimer(this, () ->
                invitationManager.cleanupExpiredInvitations(), 6000L, 6000L);

        // 初始化权限协调器
        permissionCoordinator = new IslandPermissionManager(
                islandManager, configManager, this);

        // 注册 PlaceholderAPI 扩展
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new team.starm.starmskyblock.placeholder.SkyblockExpansion(this).register();
            MessageUtil.consolePrint("&a已注册 PlaceholderAPI 扩展");
        }

        // 提前创建或加载空岛世界
        worldManager.getOrCreateSkyblockWorld();
        worldManager.getOrCreateSkyblockNether();
        worldManager.getOrCreateSkyblockEnd();

        // 注册事件监听器
        borderListener = new BorderListener(islandManager, worldManager, sqliteManager);
        getServer().getPluginManager().registerEvents(borderListener, this);
        getServer().getPluginManager().registerEvents(new PortalListener(configManager, worldManager, islandManager, sqliteManager), this);
        // 初始化设置管理器（会自动注册所有子监听器）
        new IslandSettingManager(islandManager, configManager, this);

        // 注册命令
        if (getCommand("isadmin") != null) {
            AdminCommand adminCmd = new AdminCommand(this);
            getCommand("isadmin").setExecutor(adminCmd);
            getCommand("isadmin").setTabCompleter(adminCmd);
        }

        if (getCommand("is") != null) {
            team.starm.starmskyblock.command.IslandCommand islandCmd = new team.starm.starmskyblock.command.IslandCommand(
                    this);
            getCommand("is").setExecutor(islandCmd);
            getCommand("is").setTabCompleter(islandCmd);
        }

        MessageUtil.consolePrint("&a[StarMSkyblock] 插件已启用！虚空世界已准备就绪。");
    }

    /**
     * 插件关闭入口。取消所有调度任务并关闭数据库连接。
     */
    @Override
    public void onDisable() {
        // 取消所有本插件的调度任务（防止异步任务在关闭时报错）
        org.bukkit.Bukkit.getScheduler().cancelTasks(this);

        if (sqliteManager != null) {
            sqliteManager.close();
        }
        MessageUtil.consolePrint("&c[StarMSkyblock] 插件已关闭。");
    }

    public static StarMSkyblock getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PermissionConfigManager getPermissionConfigManager() {
        return permissionConfigManager;
    }

    public SettingsConfigManager getSettingsConfigManager() {
        return settingsConfigManager;
    }

    public SignConfigManager getSignConfigManager() {
        return signConfigManager;
    }

    public SQLiteManager getSqliteManager() {
        return sqliteManager;
    }

    public SchematicManager getSchematicManager() {
        return schematicManager;
    }

    public GridManager getGridManager() {
        return gridManager;
    }

    public IslandManager getIslandManager() {
        return islandManager;
    }

    public InvitationManager getInvitationManager() {
        return invitationManager;
    }

    public SkyblockWorldManager getWorldManager() {
        return worldManager;
    }

    public IslandPermissionManager getPermissionCoordinator() {
        return permissionCoordinator;
    }

    public BorderListener getBorderListener() {
        return borderListener;
    }

    /**
     * 将 jar 包中内置的 .schem 结构文件释放到插件数据目录下的 schematics 文件夹。
     * 仅当文件不存在时才会拷贝，避免覆盖用户自定义的结构文件。
     */
    private void extractSchematics() {
        File schematicsFolder = new File(getDataFolder(), "schematics");
        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
        }

        // 内置的三个结构文件（主世界、下界、末地）
        String[] schematicFiles = {
                "default.schem",
                "default_nether.schem",
                "default_the_end.schem"
        };

        for (String fileName : schematicFiles) {
            File targetFile = new File(schematicsFolder, fileName);
            // 已存在则跳过，避免覆盖用户自定义
            if (targetFile.exists())
                continue;

            try (InputStream inputStream = getResource("schematics/" + fileName)) {
                if (inputStream == null) {
                    MessageUtil.consoleWarn("找不到内置的schematics文件: " + fileName);
                    continue;
                }
                Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                MessageUtil.consolePrint("&a已创建schematics文件: " + fileName);
            } catch (IOException e) {
                MessageUtil.consoleError("&c创建schematics文件时发生错误: " + fileName);
                e.printStackTrace();
            }
        }
    }
}