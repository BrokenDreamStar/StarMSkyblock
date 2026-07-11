package team.starm.starmskyblock.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EntityType;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.generator.VoidChunkGenerator;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.util.reflection.EnderDragonReflection;
import java.io.File;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

/**
 * 空岛世界管理器。
 * 负责创建/加载三个虚空世界（主世界、下界、末地），
 * 使用 VoidChunkGenerator 生成仅含基岩和设定生物群系的虚空地形。
 * 世界名称、环境类型和生物群系均从 ConfigManager 读取。
 */
public class SkyblockWorldManager {

    /** 配置管理器，提供世界名称、生物群系等配置 */
    private final ConfigManager configManager;
    /** 插件主类实例，用于 Multiverse 导入命令中的插件名 */
    private final StarMSkyblock plugin;
    /** 主世界实例（懒加载） */
    private World skyblockWorld;
    /** 下界世界实例（懒加载） */
    private World skyblockNether;
    /** 末地世界实例（懒加载） */
    private World skyblockEnd;

    public SkyblockWorldManager(ConfigManager configManager, StarMSkyblock plugin) {
        this.configManager = configManager;
        this.plugin = plugin;
    }

    /** 懒加载获取或创建主世界实例 */
    public World getOrCreateSkyblockWorld() {
        if (skyblockWorld == null) {
            skyblockWorld = createWorld(configManager.getWorldNameNormal(), World.Environment.NORMAL,
                    configManager.getBiomeNormal(), Biome.PLAINS);
        }
        return skyblockWorld;
    }

    /** 懒加载获取或创建下界世界实例 */
    public World getOrCreateSkyblockNether() {
        if (skyblockNether == null) {
            skyblockNether = createWorld(configManager.getWorldNameNether(), World.Environment.NETHER,
                    configManager.getBiomeNether(), Biome.NETHER_WASTES);
        }
        return skyblockNether;
    }

    /** 懒加载获取或创建末地世界实例 */
    public World getOrCreateSkyblockEnd() {
        if (skyblockEnd == null) {
            skyblockEnd = createWorld(configManager.getWorldNameEnd(), World.Environment.THE_END,
                    configManager.getBiomeEnd(), Biome.THE_END);
        }
        return skyblockEnd;
    }

    /**
     * 创建或加载一个空岛虚空世界。
     * <p>若世界目录已存在（含 level.dat）则直接加载；否则用 {@link VoidChunkGenerator} 创建新世界，
     * 设置出生点、按需向 Multiverse-Core 注册、并对末地禁用末影龙战斗。</p>
     */
    @SuppressWarnings({"deprecation"})
    private World createWorld(String worldName, World.Environment environment, String biomeName, Biome defaultBiome) {
        World world = Bukkit.getWorld(worldName);

        String dimensionName = switch (environment) {
            case NORMAL -> "主世界";
            case NETHER -> "下界";
            case THE_END -> "末地";
            default -> environment.name();
        };

        if (world == null) {
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            File levelFile = new File(worldFolder, "level.dat");
            boolean existingData = worldFolder.isDirectory() && levelFile.isFile();

            if (existingData) {
                MessageUtil.consolePrint("正在加载已有空岛世界[" + dimensionName + "]: " + worldName);
            } else {
                MessageUtil.consolePrint("正在创建空岛世界[" + dimensionName + "]: " + worldName);
            }

            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(environment);
            creator.type(WorldType.NORMAL);
            creator.generateStructures(false);

            Biome biome = defaultBiome;
            try {
                NamespacedKey key = NamespacedKey.minecraft(biomeName.toLowerCase());
                Biome configBiome = Registry.BIOME.get(key);
                if (configBiome != null) {
                    biome = configBiome;
                } else {
                    MessageUtil.consoleWarn("未知的生物群系配置: " + biomeName + "，将使用默认的 " + defaultBiome.getKey().getKey());
                }
            } catch (Exception e) {
                MessageUtil.consoleError("解析生物群系配置时发生错误: " + biomeName + "，将使用默认的 " + defaultBiome.getKey().getKey());
            }
            creator.generator(new VoidChunkGenerator(biome));

            world = Bukkit.createWorld(creator);

            if (world != null) {
                if (!existingData) {
                    world.setSpawnLocation(0, 80, 0);
                }

                if (Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
                    String envArg = switch (environment) {
                        case NETHER -> "nether";
                        case THE_END -> "end";
                        default -> "normal";
                    };
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "mv import " + worldName + " " + envArg + " -g " + plugin.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "mv modify set generator " + plugin.getName() + " " + worldName);
                }

                if (environment == World.Environment.THE_END) {
                    disableEnderDragonFight(world);
                }

                MessageUtil.consolePrint("空岛世界[" + dimensionName + "]准备就绪！名称: " + worldName);
            } else {
                MessageUtil.consoleError("空岛世界[" + dimensionName + "]加载或创建失败: " + worldName);
            }
        }

        return world;
    }

    public World getSkyblockWorld() {
        return skyblockWorld;
    }

    public World getSkyblockNether() {
        return skyblockNether;
    }

    public World getSkyblockEnd() {
        return skyblockEnd;
    }

    /**
     * 判断是否为空岛世界（主世界/下界/末地）
     */
    public boolean isSkyblockWorld(World world) {
        if (world == null) return false;
        return world.equals(skyblockWorld) || world.equals(skyblockNether) || world.equals(skyblockEnd);
    }

    /**
     * 判断世界名称是否为空岛世界
     */
    public boolean isSkyblockWorldName(String worldName) {
        if (worldName == null) return false;
        return worldName.equals(configManager.getWorldNameNormal())
                || worldName.equals(configManager.getWorldNameNether())
                || worldName.equals(configManager.getWorldNameEnd());
    }

    /**
     * 判断世界名称是否为空岛世界的某个环境
     */
    public boolean isNormalWorld(String worldName) {
        return worldName != null && worldName.equals(configManager.getWorldNameNormal());
    }

    /** 判断世界名称是否为下界世界 */
    public boolean isNetherWorld(String worldName) {
        return worldName != null && worldName.equals(configManager.getWorldNameNether());
    }

    /** 判断世界名称是否为末地世界 */
    public boolean isEndWorld(String worldName) {
        return worldName != null && worldName.equals(configManager.getWorldNameEnd());
    }

    /**
     * 判断世界名称是否为配置的公共世界
     *
     * @param worldName 世界名称
     * @return 如果该世界在 config.yml 的 public-worlds 列表中则返回 true
     */
    public boolean isPublicWorld(String worldName) {
        if (worldName == null) {
            return false;
        }
        return configManager.isPublicWorld(worldName);
    }

    /**
     * 在末地世界禁用末影龙战斗：标记为已击败、通过反射关闭战斗机制、并清除已存在的末影龙与末地水晶。
     * 用于空岛末地不需要末影龙战斗的场景，避免龙破坏岛屿。
     */
    private void disableEnderDragonFight(World world) {
        try {
            DragonBattle battle = world.getEnderDragonBattle();
            if (battle != null) {
                battle.setPreviouslyKilled(true);
            }
        } catch (Exception e) {
            MessageUtil.consoleWarn("无法通过 API 标记末影龙为已击败: " + e.getMessage());
        }

        EnderDragonReflection.disableDragonFight(world);

        world.getEntities().forEach(entity -> {
            if (entity.getType() == EntityType.ENDER_DRAGON || entity.getType() == EntityType.END_CRYSTAL) {
                entity.remove();
            }
        });
    }
}
