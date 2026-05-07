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
import team.starm.starmskyblock.listener.EntityPortalListener;
import team.starm.starmskyblock.listener.PlayerNetherListener;
import team.starm.starmskyblock.listener.PortalListener;
import team.starm.starmskyblock.permission.IslandPermissionManager;
import team.starm.starmskyblock.util.ColorUtil;
import team.starm.starmskyblock.world.SkyblockWorldManager;

public class StarMSkyblock extends JavaPlugin {

    private static StarMSkyblock instance;
    private ConfigManager configManager;
    private PermissionConfigManager permissionConfigManager;
    private SQLiteManager sqliteManager;
    private SchematicManager schematicManager;
    private GridManager gridManager;
    private IslandManager islandManager;
    private InvitationManager invitationManager;
    private SkyblockWorldManager worldManager;
    private IslandPermissionManager permissionCoordinator;
    private BorderListener borderListener;

    @Override
    public void onEnable() {
        instance = this;

        // 初始化配置
        configManager = new ConfigManager(this);
        configManager.initialize();

        // 初始化权限配置
        permissionConfigManager = new PermissionConfigManager(this);
        permissionConfigManager.initialize();

        // 释放内置的schematics文件
        extractSchematics();

        // 初始化数据库
        sqliteManager = new SQLiteManager(this);
        sqliteManager.init();

        // 初始化 FAWE 结构管理器
        schematicManager = new SchematicManager(this);

        // 初始化网格系统和岛屿管理器
        gridManager = new GridManager(configManager);
        islandManager = new IslandManager(this, configManager, gridManager, sqliteManager);
        invitationManager = new InvitationManager(this, islandManager);

        // 初始化权限协调器
        permissionCoordinator = new IslandPermissionManager(
                islandManager, configManager, this);
        permissionCoordinator.initializeEventListeners(this);

        // 初始化世界管理器
        worldManager = new SkyblockWorldManager(this);

        // 提前创建或加载空岛世界
        worldManager.getOrCreateSkyblockWorld();
        worldManager.getOrCreateSkyblockNether();
        worldManager.getOrCreateSkyblockEnd();

        // 注册事件监听器
        borderListener = new BorderListener(this);
        getServer().getPluginManager().registerEvents(borderListener, this);
        getServer().getPluginManager().registerEvents(new PortalListener(this), this);
        getServer().getPluginManager().registerEvents(new EntityPortalListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerNetherListener(this), this);

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

        ColorUtil.consolePrint("&a[StarMSkyblock] 插件已启用！虚空世界已准备就绪。");
    }

    @Override
    public void onDisable() {
        if (sqliteManager != null) {
            sqliteManager.close();
        }
        ColorUtil.consolePrint("&c[StarMSkyblock] 插件已关闭。");
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

    private void extractSchematics() {
        // （保持不变）
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
                    getLogger().warning("找不到内置的schematics文件: " + fileName);
                    continue;
                }
                Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("已创建schematics文件: " + fileName);
            } catch (IOException e) {
                getLogger().severe("创建schematics文件时发生错误: " + fileName);
                e.printStackTrace();
            }
        }
    }
}