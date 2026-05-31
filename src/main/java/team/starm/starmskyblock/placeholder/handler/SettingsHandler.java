package team.starm.starmskyblock.placeholder.handler;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.setting.IslandSetting;

import java.util.Optional;
import java.util.UUID;

public class SettingsHandler {

    public static final String PREFIX = "islandsettings_";

    private final StarMSkyblock plugin;

    public SettingsHandler(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

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
            throwable.printStackTrace();
        }

        return null;
    }

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
