package team.starm.starmskyblock;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.net.URI;

import team.starm.starmskyblock.command.AdminCommand;
import team.starm.starmskyblock.config.ExperienceConfig;
import team.starm.starmskyblock.config.AuraSkillsContributionConfig;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.GeneratorConfigManager;
import team.starm.starmskyblock.config.PermissionConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.config.SettingsConfigManager;
import team.starm.starmskyblock.config.UpgradeConfigManager;
import team.starm.starmskyblock.database.IslandRepository;
import team.starm.starmskyblock.database.PlayerRepository;
import team.starm.starmskyblock.database.SQLiteManager;
import team.starm.starmskyblock.generator.SchematicManager;
import team.starm.starmskyblock.grid.GridManager;
import team.starm.starmskyblock.island.InvitationManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.level.LevelManager;
import team.starm.starmskyblock.listener.BlockPlaceListener;
import team.starm.starmskyblock.listener.BorderListener;
import team.starm.starmskyblock.listener.CobblestoneGeneratorListener;
import team.starm.starmskyblock.listener.EndProtectionListener;
import team.starm.starmskyblock.listener.IslandBoundaryListener;
import team.starm.starmskyblock.listener.ObsidianToLavaListener;
import team.starm.starmskyblock.listener.PortalListener;
import team.starm.starmskyblock.listener.RespawnListener;
import team.starm.starmskyblock.listener.TeleportCountdownListener;
import team.starm.starmskyblock.setting.IslandSettingManager;
import team.starm.starmskyblock.permission.IslandPermissionManager;
import team.starm.starmskyblock.placeholder.SkyblockExpansion;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.message.LanguageManager;
import team.starm.starmskyblock.util.SkullManager;
import team.starm.starmskyblock.bridge.StarMSkyblockHook;
import me.arasple.mc.trmenu.module.internal.script.js.JavaScriptAgent;
import team.starm.starmskyblock.world.SkyblockWorldManager;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.config.TaskConfigScanner;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginEnableEvent;
import team.starm.starmskyblock.command.IslandCommand;

/**
 * StarMSkyblock 插件的主入口类。
 * 继承 JavaPlugin，负责在服务器启动时初始化所有子系统：
 * 配置管理器、数据库、结构加载器、网格系统、岛屿管理器、世界管理器、
 * 邀请系统、权限协调器、监听器以及 PlaceholderAPI 扩展。
 * 也是各个管理器单例的集中持有者（通过 getInstance() 访问）。
 */
public class StarMSkyblock extends JavaPlugin {

    /**
     * 插件单例实例
     */
    private static StarMSkyblock instance;

    // ========== 配置管理器 ==========
    private ConfigManager configManager;
    private PermissionConfigManager permissionConfigManager;
    private SettingsConfigManager settingsConfigManager;
    private GeneratorConfigManager generatorConfigManager;
    private UpgradeConfigManager upgradeConfigManager;

    // ========== 数据与生成 ==========
    private SQLiteManager sqliteManager;
    private PlayerRepository playerRepo;
    private SchematicManager schematicManager;
    private GridManager gridManager;
    private IslandManager islandManager;

    // ========== 功能模块 ==========
    private InvitationManager invitationManager;
    private SkyblockWorldManager worldManager;
    private IslandPermissionManager permissionCoordinator;
    private BorderListener borderListener;
    private TeleportCountdownListener teleportCountdownListener;

    private SkyblockExpansion skyblockExpansion;

    // ========== 等级系统 ==========
    private ExperienceConfig experienceConfig;
    private AuraSkillsContributionConfig auraskillsContributionConfig;
    private LevelManager levelManager;

    // ========== i18n ==========
    private LanguageManager languageManager;

    // ========== 公共区域 ==========
    private PublicAreaConfigManager publicAreaConfigManager;

    // ========== 锁定区域 ==========
    private LockedAreaConfigManager lockedAreaConfigManager;

    // ========== 任务系统 ==========
    private TaskConfigScanner taskConfigScanner;
    private TaskManager taskManager;

    // Vault 经济
    private Economy economy;

    // 具名监听器字段（onDisable 时由 HandlerList.unregisterAll(this) 统一注销）
    private Listener skullRefreshListener;
    private Listener trMenuLateLoadListener;

    /**
     * 插件启用入口。将初始化分解为独立步骤，保证依赖关系正确。
     */
    @Override
    public void onEnable() {
        instance = this;
        printLogo();

        if (!checkWorldEdit()) return;
        initConfigs();
        initLanguage();
        // extractSchematics 是纯文件 IO,异步执行不阻塞主线程启动链
        Bukkit.getScheduler().runTaskAsynchronously(this, this::extractSchematics);
        initDatabase();         // 必须在 initTasks() 之前，因为 TaskManager 依赖 PlayerRepository
        initTasks();
        initSchematicManager();
        initGridAndIslands();
        initWorlds();
        initLevelSystem();
        initInvitations();
        initPermissions();
        registerListeners();
        registerCommands();

        registerIntegrations();
        preWarmWorlds();

        MessageUtil.consolePrint("插件已准备就绪！");
    }

    /***
     *   ____    _                    __  __   ____    _              _       _                  _
     *  / ___|  | |_    __ _   _ __  |  \/  | / ___|  | | __  _   _  | |__   | |   ___     ___  | | __
     *  \___ \  | __|  / _` | | '__| | |\/| | \___ \  | |/ / | | | | | '_ \  | |  / _ \   / __| | |/ /
     *   ___) | | |_  | (_| | | |    | |  | |  ___) | |   <  | |_| | | |_) | | | | (_) | | (__  |   <
     *  |____/   \__|  \__,_| |_|    |_|  |_| |____/  |_|\_\  \__, | |_.__/  |_|  \___/   \___| |_|\_\
     *                                                        |___/
     */

    private void printLogo() {
        Bukkit.getConsoleSender().sendMessage(
                MessageUtil.colorize(
                        """
                                
                                <gradient:#14bcfe:#495aff>\
                                   ____    _                    __  __   ____    _              _       _                  _   \s
                                  / ___|  | |_    __ _   _ __  |  \\/  | / ___|  | | __  _   _  | |__   | |   ___     ___  | | __
                                  \\___ \\  | __|  / _` | | '__| | |\\/| | \\___ \\  | |/ / | | | | | '_ \\  | |  / _ \\   / __| | |/ /
                                   ___) | | |_  | (_| | | |    | |  | |  ___) | |   <  | |_| | | |_) | | | | (_) | | (__  |   <\s
                                  |____/   \\__|  \\__,_| |_|    |_|  |_| |____/  |_|\\_\\  \\__, | |_.__/  |_|  \\___/   \\___| |_|\\_\\
                                                                                        |___/                                    \
                                </gradient>"""
                )
        );
    }

    private boolean checkWorldEdit() {
        if (getServer().getPluginManager().getPlugin("WorldEdit") == null) {
            MessageUtil.consoleError("未检测到 WorldEdit 或 FastAsyncWorldEdit！");
            MessageUtil.consoleError("StarMSkyblock 需要 WorldEdit 或 FastAsyncWorldEdit  才能运行。");
            MessageUtil.consoleError("请安装 WorldEdit 或 FastAsyncWorldEdit 后重启服务器。");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
        return true;
    }

    private void initConfigs() {
        configManager = new ConfigManager(this);
        configManager.initialize();

        permissionConfigManager = new PermissionConfigManager(this);
        permissionConfigManager.initialize();

        settingsConfigManager = new SettingsConfigManager(this);
        settingsConfigManager.initialize();

        generatorConfigManager = new GeneratorConfigManager(this);
        generatorConfigManager.initialize();

        upgradeConfigManager = new UpgradeConfigManager(this);
        upgradeConfigManager.initialize();

        publicAreaConfigManager = new PublicAreaConfigManager(permissionConfigManager);
        publicAreaConfigManager.initialize();

        lockedAreaConfigManager = new LockedAreaConfigManager(permissionConfigManager);
        lockedAreaConfigManager.initialize();
    }

    private void initLanguage() {
        languageManager = new LanguageManager(this);
        languageManager.initialize();
        MessageUtil.setLanguageManager(languageManager);
    }

    private void initDatabase() {
        sqliteManager = new SQLiteManager(getDataFolder());
        sqliteManager.init();
        playerRepo = new PlayerRepository(sqliteManager);
        // warmUpPlayerNameCache 是纯 DB 全表扫描 + 缓存填充,异步执行避免主线程启动阻塞
        Bukkit.getScheduler().runTaskAsynchronously(this, playerRepo::warmUpPlayerNameCache);
        SkullManager.initDatabase(sqliteManager);
    }

    private void initTasks() {
        taskConfigScanner = new TaskConfigScanner(this);
        taskConfigScanner.initialize();
        taskManager = new TaskManager(this, taskConfigScanner);
        taskManager.init();
    }

    private void initSchematicManager() {
        schematicManager = new SchematicManager(new File(getDataFolder(), "schematics"), configManager.isUseFawe());
    }

    private void initGridAndIslands() {
        gridManager = new GridManager(configManager);
        IslandRepository islandRepo = new IslandRepository(sqliteManager);
        islandManager = new IslandManager(configManager, permissionConfigManager, settingsConfigManager,
                gridManager, islandRepo, playerRepo);
    }

    private void initWorlds() {
        worldManager = new SkyblockWorldManager(configManager, this);
    }

    private void initLevelSystem() {
        experienceConfig = new ExperienceConfig(this);
        experienceConfig.initialize();

        auraskillsContributionConfig = new AuraSkillsContributionConfig(this);
        auraskillsContributionConfig.initialize();

        levelManager = new LevelManager(this, experienceConfig, auraskillsContributionConfig,
                islandManager, worldManager);
    }

    private void initInvitations() {
        invitationManager = new InvitationManager(islandManager, configManager, worldManager);
        getServer().getScheduler().runTaskTimer(this, () ->
                invitationManager.cleanupExpiredInvitations(), 6000L, 6000L);
    }

    private void initPermissions() {
        permissionCoordinator = new IslandPermissionManager(islandManager, configManager, publicAreaConfigManager, lockedAreaConfigManager, this, worldManager);
    }

    private void registerIntegrations() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            skyblockExpansion = new SkyblockExpansion(this);
            skyblockExpansion.register();
            MessageUtil.consolePrint("已注册 PlaceholderAPI 扩展");
        }

        skullRefreshListener = new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                Player player = event.getPlayer();
                getServer().getScheduler().runTaskAsynchronously(
                        StarMSkyblock.this,
                        () -> SkullManager.refreshTexture(player.getUniqueId(), player.getName()));
            }
        };
        getServer().getPluginManager().registerEvents(skullRefreshListener, this);

        // TrMenu 后加载场景：PluginEnableEvent 触发时再 hook 一次
        trMenuLateLoadListener = new Listener() {
            @EventHandler
            public void onPluginEnable(PluginEnableEvent event) {
                if ("TrMenu".equals(event.getPlugin().getName())) {
                    hookTrMenu();
                }
            }
        };
        getServer().getPluginManager().registerEvents(trMenuLateLoadListener, this);

        if (getServer().getPluginManager().getPlugin("TrMenu") != null) {
            hookTrMenu();
        }

        setupVault();
    }

    /**
     * 注册 TrMenu JS 桥接 + 提取菜单文件。幂等：putBinding 走 Map.put，extractSkyblockMenu 内部判存在。
     * try/catch Throwable 兜住 TrMenu 缺类时的 NoClassDefFoundError / LinkageError。
     */
    private void hookTrMenu() {
        try {
            JavaScriptAgent.INSTANCE.putBinding("StarMSkyblockAPI", new StarMSkyblockHook());
            MessageUtil.consolePrint("已注册 TrMenu JS 物品源桥接");

            File menuDir = new File("plugins/TrMenu/menus/skyblockmenu");
            if (!menuDir.exists()) {
                extractSkyblockMenu();
            }
        } catch (Throwable t) {
            MessageUtil.consoleError("TrMenu hook 失败: " + t.getMessage(), t);
        }
    }

    private void setupVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            MessageUtil.consolePrint("未检测到 Vault，升级功能将不可用");
            return;
        }

        // 立即尝试获取——未获取到时 getEconomy() 在命令执行时自动重试
        if (getEconomy() != null) {
            MessageUtil.consolePrint("已注册 Vault 经济系统: " + economy.getName());
        }
    }

    private void preWarmWorlds() {
        worldManager.getOrCreateSkyblockWorld();
        worldManager.getOrCreateSkyblockNether();
        worldManager.getOrCreateSkyblockEnd();
    }

    private void registerListeners() {
        borderListener = new BorderListener(islandManager, worldManager, playerRepo, configManager);
        getServer().getPluginManager().registerEvents(borderListener, this);
        getServer().getPluginManager().registerEvents(
                new PortalListener(configManager, worldManager, islandManager, playerRepo), this);

        teleportCountdownListener = new TeleportCountdownListener(this);
        getServer().getPluginManager().registerEvents(teleportCountdownListener, this);

        new IslandSettingManager(islandManager, configManager, publicAreaConfigManager, lockedAreaConfigManager, this, worldManager);

        getServer().getPluginManager().registerEvents(new EndProtectionListener(worldManager), this);

        getServer().getPluginManager().registerEvents(new BlockPlaceListener(worldManager, configManager), this);

        getServer().getPluginManager().registerEvents(
                new IslandBoundaryListener(islandManager, worldManager, configManager), this);

        if (generatorConfigManager.isEnabled()) {
            getServer().getPluginManager().registerEvents(
                    new CobblestoneGeneratorListener(generatorConfigManager, islandManager, worldManager), this);
//            MessageUtil.consolePrint("已注册刷石机监听器");
        }

        if (configManager.isObsidianToLava()) {
            getServer().getPluginManager().registerEvents(
                    new ObsidianToLavaListener(islandManager, configManager, publicAreaConfigManager, lockedAreaConfigManager, this, worldManager), this);
//            MessageUtil.consolePrint("已注册黑曜石转熔岩监听器");
        }

        getServer().getPluginManager().registerEvents(
                new RespawnListener(islandManager, configManager, worldManager), this);
    }

    private void registerCommands() {
        if (getCommand("isadmin") != null) {
            AdminCommand adminCmd = new AdminCommand(this);
            adminCmd.registerCommands();
            getCommand("isadmin").setExecutor(adminCmd);
            getCommand("isadmin").setTabCompleter(adminCmd);
        }

        if (getCommand("is") != null) {
            IslandCommand islandCmd =
                    new IslandCommand(this);
            islandCmd.registerCommands();
            getCommand("is").setExecutor(islandCmd);
            getCommand("is").setTabCompleter(islandCmd);
        }

    }

    /**
     * 插件关闭入口。取消所有调度任务并关闭数据库连接。
     */
    @Override
    public void onDisable() {
        if (taskManager != null) {
            taskManager.saveAll();
        }

        // 注销本插件注册的所有 Listener（含权限/设置组合监听器、skullRefresh、trMenuLateLoad 等），
        // 避免 /reload 后旧监听器残留导致内存泄漏和重复触发
        HandlerList.unregisterAll(this);

        Bukkit.getScheduler().cancelTasks(this);

        if (sqliteManager != null) {
            sqliteManager.close();
        }
        MessageUtil.consolePrint("&c插件已关闭。");
    }

    public static StarMSkyblock getInstance() {
        return instance;
    }

    public LockedAreaConfigManager getLockedAreaConfigManager() {
        return lockedAreaConfigManager;
    }

    public PublicAreaConfigManager getPublicAreaConfigManager() {
        return publicAreaConfigManager;
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

    public GeneratorConfigManager getGeneratorConfigManager() {
        return generatorConfigManager;
    }

    public UpgradeConfigManager getUpgradeConfigManager() {
        return upgradeConfigManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public Economy getEconomy() {
        if (economy == null) {
            var rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null && rsp.getPlugin().isEnabled()) {
                economy = rsp.getProvider();
            }
        }
        return economy;
    }

    public SQLiteManager getSqliteManager() {
        return sqliteManager;
    }

    public PlayerRepository getPlayerRepo() {
        return playerRepo;
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

    public TeleportCountdownListener getTeleportCountdownListener() {
        return teleportCountdownListener;
    }

    public SkyblockExpansion getSkyblockExpansion() {
        return skyblockExpansion;
    }

    public ExperienceConfig getExperienceConfig() {
        return experienceConfig;
    }

    public AuraSkillsContributionConfig getAuraskillsContributionConfig() {
        return auraskillsContributionConfig;
    }

    public LevelManager getLevelManager() {
        return levelManager;
    }

    public TaskConfigScanner getTaskConfigManager() {
        return taskConfigScanner;
    }

    public TaskManager getTaskManager() {
        return taskManager;
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

        String[] schematicFiles = {
                "default.schem",
                "default_nether.schem",
                "default_the_end.schem"
        };

        int extracted = 0;
        for (String fileName : schematicFiles) {
            File targetFile = new File(schematicsFolder, fileName);
            if (targetFile.exists()) {
                extracted++;
                continue;
            }

            try (InputStream inputStream = getResource("schematics/" + fileName)) {
                if (inputStream == null) {
                    MessageUtil.consoleWarn("找不到内置的schematics文件: " + fileName);
                    continue;
                }
                Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                MessageUtil.consoleError("创建schematics文件时发生错误: " + fileName, e);
            }
            extracted++;
        }
        MessageUtil.consolePrint("已加载 " + extracted + " 个岛屿模板结构文件");
    }

    private void extractSkyblockMenu() {
        try {
            URI jarUri = getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            Path trMenuMenus = new File("plugins/TrMenu/menus/skyblockmenu").toPath();
            int[] count = {0};

            try (FileSystem fs = FileSystems.newFileSystem(Path.of(jarUri), (ClassLoader) null)) {
                Path menuRoot = fs.getPath("skyblockmenu");
                Files.walk(menuRoot).forEach(source -> {
                    try {
                        Path relative = menuRoot.relativize(source);
                        Path target = trMenuMenus.resolve(relative.toString());
                        if (Files.isDirectory(source)) {
                            target.toFile().mkdirs();
                        } else if (!target.toFile().exists()) {
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                            count[0]++;
                        }
                    } catch (IOException e) {
                        MessageUtil.consoleError("提取菜单文件时出错: " + source, e);
                    }
                });
            }

            if (count[0] > 0) {
                MessageUtil.consolePrint("已创建 " + count[0] + " 个 TrMenu 菜单文件");
            }
        } catch (Exception e) {
            MessageUtil.consoleError("提取 TrMenu 菜单文件时出错", e);
        }
    }
}