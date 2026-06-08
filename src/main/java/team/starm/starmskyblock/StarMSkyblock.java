package team.starm.starmskyblock;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import team.starm.starmskyblock.command.AdminCommand;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.GeneratorConfigManager;
import team.starm.starmskyblock.config.PermissionConfigManager;
import team.starm.starmskyblock.config.SettingsConfigManager;
import team.starm.starmskyblock.config.SignConfigManager;
import team.starm.starmskyblock.config.UpgradeConfigManager;
import team.starm.starmskyblock.database.IslandRepository;
import team.starm.starmskyblock.database.PlayerRepository;
import team.starm.starmskyblock.database.SQLiteManager;
import team.starm.starmskyblock.generator.SchematicManager;
import team.starm.starmskyblock.grid.GridManager;
import team.starm.starmskyblock.island.InvitationManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.listener.BlockPlaceListener;
import team.starm.starmskyblock.listener.BorderListener;
import team.starm.starmskyblock.listener.CobblestoneGeneratorListener;
import team.starm.starmskyblock.listener.EndProtectionListener;
import team.starm.starmskyblock.listener.ObsidianToLavaListener;
import team.starm.starmskyblock.listener.PortalListener;
import team.starm.starmskyblock.listener.RespawnListener;
import team.starm.starmskyblock.listener.TeleportCountdownListener;
import team.starm.starmskyblock.setting.IslandSettingManager;
import team.starm.starmskyblock.permission.IslandPermissionManager;
import team.starm.starmskyblock.placeholder.SkyblockExpansion;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.util.SkullManager;
import team.starm.starmskyblock.bridge.StarMSkyblockHook;
import me.arasple.mc.trmenu.module.internal.script.js.JavaScriptAgent;
import team.starm.starmskyblock.world.SkyblockWorldManager;
import team.starm.starmskyblock.task.TaskManager;
import team.starm.starmskyblock.task.config.TaskConfigScanner;

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
    private SignConfigManager signConfigManager;
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

    // ========== 任务系统 ==========
    private TaskConfigScanner taskConfigScanner;
    private TaskManager taskManager;

    // Vault 经济
    private net.milkbowl.vault.economy.Economy economy;

    /**
     * 插件启用入口。将初始化分解为独立步骤，保证依赖关系正确。
     */
    @Override
    public void onEnable() {
        instance = this;
        printLogo();

        if (!checkWorldEdit()) return;
        initConfigs();
        extractSchematics();
        initDatabase();
        initTasks();
        initSchematicManager();
        initGridAndIslands();
        initWorlds();
        initInvitations();
        initPermissions();
        registerIntegrations();
        preWarmWorlds();
        registerListeners();
        registerCommands();

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

        signConfigManager = new SignConfigManager(this);
        signConfigManager.initialize();

        generatorConfigManager = new GeneratorConfigManager(this);
        generatorConfigManager.initialize();

        upgradeConfigManager = new UpgradeConfigManager(this);
        upgradeConfigManager.initialize();
    }

    private void initDatabase() {
        sqliteManager = new SQLiteManager(getDataFolder());
        sqliteManager.init();
        playerRepo = new PlayerRepository(sqliteManager);
        playerRepo.warmUpPlayerNameCache();
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

    private void initInvitations() {
        invitationManager = new InvitationManager(islandManager, configManager, worldManager);
        getServer().getScheduler().runTaskTimer(this, () ->
                invitationManager.cleanupExpiredInvitations(), 6000L, 6000L);
    }

    private void initPermissions() {
        permissionCoordinator = new IslandPermissionManager(islandManager, configManager, this);
    }

    private void registerIntegrations() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            skyblockExpansion = new SkyblockExpansion(this);
            skyblockExpansion.register();
            MessageUtil.consolePrint("已注册 PlaceholderAPI 扩展");
        }

        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                org.bukkit.entity.Player player = event.getPlayer();
                getServer().getScheduler().runTaskAsynchronously(
                        StarMSkyblock.this,
                        () -> SkullManager.refreshTexture(player.getUniqueId(), player.getName()));
            }
        }, this);

        if (getServer().getPluginManager().getPlugin("TrMenu") != null) {
            JavaScriptAgent.INSTANCE.putBinding("StarMSkyblockAPI", new StarMSkyblockHook());
            MessageUtil.consolePrint("已注册 TrMenu JS 物品源桥接");
        }

        setupVault();
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
        borderListener = new BorderListener(islandManager, worldManager, playerRepo);
        getServer().getPluginManager().registerEvents(borderListener, this);
        getServer().getPluginManager().registerEvents(
                new PortalListener(configManager, worldManager, islandManager, playerRepo), this);

        teleportCountdownListener = new TeleportCountdownListener(this);
        getServer().getPluginManager().registerEvents(teleportCountdownListener, this);

        new IslandSettingManager(islandManager, configManager, this);

        getServer().getPluginManager().registerEvents(new EndProtectionListener(worldManager), this);

        getServer().getPluginManager().registerEvents(new BlockPlaceListener(worldManager, configManager), this);

        if (generatorConfigManager.isEnabled()) {
            getServer().getPluginManager().registerEvents(
                    new CobblestoneGeneratorListener(generatorConfigManager, islandManager, worldManager), this);
//            MessageUtil.consolePrint("已注册刷石机监听器");
        }

        if (configManager.isObsidianToLava()) {
            getServer().getPluginManager().registerEvents(
                    new ObsidianToLavaListener(islandManager, configManager), this);
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
            team.starm.starmskyblock.command.IslandCommand islandCmd =
                    new team.starm.starmskyblock.command.IslandCommand(this);
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

        org.bukkit.Bukkit.getScheduler().cancelTasks(this);

        if (sqliteManager != null) {
            sqliteManager.close();
        }
        MessageUtil.consolePrint("&c插件已关闭。");
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

    public GeneratorConfigManager getGeneratorConfigManager() {
        return generatorConfigManager;
    }

    public UpgradeConfigManager getUpgradeConfigManager() {
        return upgradeConfigManager;
    }

    public net.milkbowl.vault.economy.Economy getEconomy() {
        if (economy == null) {
            var rsp = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
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

        for (String fileName : schematicFiles) {
            File targetFile = new File(schematicsFolder, fileName);
            if (targetFile.exists())
                continue;

            try (InputStream inputStream = getResource("schematics/" + fileName)) {
                if (inputStream == null) {
                    MessageUtil.consoleWarn("找不到内置的schematics文件: " + fileName);
                    continue;
                }
                Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                MessageUtil.consolePrint("已创建schematics文件: " + fileName);
            } catch (IOException e) {
                MessageUtil.consoleError("创建schematics文件时发生错误: " + fileName);
                e.printStackTrace();
            }
        }
    }
}