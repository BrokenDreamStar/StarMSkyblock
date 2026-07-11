package team.starm.starmskyblock.placeholder.handler;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.setting.IslandSetting;

import team.starm.starmskyblock.message.MessageUtil;
import java.util.Optional;
import java.util.UUID;

/**
 * 岛屿设置占位符处理器（前缀 {@code islandsettings_}）。
 * <p>读取玩家所属岛屿的某项 {@link IslandSetting} 开关状态并渲染为是/否。
 */
public class SettingsHandler {

    /** 占位符前缀 */
    public static final String PREFIX = "islandsettings_";

    private final StarMSkyblock plugin;

    public SettingsHandler(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * 处理岛屿设置占位符请求。
     *
     * @param player 请求占位符的玩家
     * @param params 占位符参数（含前缀）
     * @return 渲染结果，无法识别时返回 null
     */
    public String handle(Player player, String params) {

        try {

            IslandManager islandManager = plugin.getIslandManager();

            String settingName =
                    params.substring(PREFIX.length());

            return getIslandSettingStatus(
                    islandManager,
                    player.getUniqueId(),
                    settingName
            );

        } catch (Throwable throwable) {
            MessageUtil.consoleError("处理设置占位符时发生错误", throwable);
        }

        return null;
    }

    /** 查询玩家所属岛屿的某项设置开关状态，启用返回"&a是"，关闭返回"&c否"。 */
    private String getIslandSettingStatus(
            IslandManager islandManager,
            UUID playerUuid,
            String settingName
    ) {

        try {

            Optional<Island> islandOpt =
                    islandManager.getIslandByPlayer(playerUuid);

            if (islandOpt.isEmpty()) {
                return null;
            }

            Island island = islandOpt.get();

            IslandSetting setting =
                    IslandSetting.valueOf(settingName.toUpperCase());

            boolean enabled =
                    island.getSetting(setting);

            return enabled ? "&a是" : "&c否";

        } catch (Throwable ignored) {
        }

        return null;
    }
}
