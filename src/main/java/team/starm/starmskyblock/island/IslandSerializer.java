package team.starm.starmskyblock.island;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.setting.IslandSetting;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 岛屿数据 JSON 序列化/反序列化工具类。
 * 负责 settings 和 permissions 的 JSON 编解码及旧格式 key 迁移。
 */
public final class IslandSerializer {

    private static final Gson GSON = new GsonBuilder().create();

    private IslandSerializer() {}

    // ==================== Settings ====================

    public static String settingsToJson(Map<IslandSetting, Boolean> settingValues) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (IslandSetting setting : IslandSetting.values()) {
            map.put(setting.name(), settingValues.get(setting));
        }
        return GSON.toJson(map);
    }

    @SuppressWarnings("unchecked")
    public static void settingsFromJson(String json, Map<IslandSetting, Boolean> settingValues) {
        if (json == null || json.isEmpty() || json.equals("{}")) return;
        try {
            Map<String, Object> raw = GSON.fromJson(json, Map.class);
            if (raw == null) return;
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (!(entry.getValue() instanceof Boolean)) continue;
                String key = entry.getKey();
                IslandSetting setting = null;
                try {
                    setting = IslandSetting.valueOf(key.toUpperCase());
                } catch (IllegalArgumentException e) {
                    setting = legacyKeyToSetting(key);
                }
                if (setting != null) {
                    settingValues.put(setting, (Boolean) entry.getValue());
                }
            }
        } catch (Exception ignored) {}
    }

    private static IslandSetting legacyKeyToSetting(String key) {
        return switch (key) {
            case "pvp" -> IslandSetting.PVP;
            case "animalSpawn" -> IslandSetting.ANIMAL_SPAWN;
            case "monsterSpawn" -> IslandSetting.MONSTER_SPAWN;
            case "spawnerSpawn" -> IslandSetting.SPAWNER_SPAWN;
            case "fireSpread" -> IslandSetting.FIRE_SPREAD;
            case "endermanGrief" -> IslandSetting.ENDERMAN_GRIEF;
            case "ghastFireballGrief" -> IslandSetting.GHAST_FIREBALL_GRIEF;
            case "creeperExplosion" -> IslandSetting.CREEPER_EXPLOSION;
            case "tntExplosion" -> IslandSetting.TNT_EXPLOSION;
            case "witherGrief" -> IslandSetting.WITHER_GRIEF;
            default -> null;
        };
    }

    // ==================== Permissions ====================

    public static String permissionsToJson(Map<IslandPermission, Integer> permissionMinLevels) {
        Map<String, Integer> stringMap = new LinkedHashMap<>();
        for (IslandPermission perm : IslandPermission.values()) {
            Integer level = permissionMinLevels.get(perm);
            if (level != null) {
                stringMap.put(perm.name(), level);
            }
        }
        return GSON.toJson(stringMap);
    }

    @SuppressWarnings("unchecked")
    public static void permissionsFromJson(String json, Map<IslandPermission, Integer> permissionMinLevels) {
        permissionMinLevels.clear();
        if (json == null || json.isEmpty() || json.equals("{}")) return;
        try {
            Map<String, Object> raw = GSON.fromJson(json, Map.class);
            if (raw == null) return;
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                try {
                    IslandPermission perm = IslandPermission.valueOf(entry.getKey());
                    Object value = entry.getValue();
                    if (value instanceof Number) {
                        permissionMinLevels.put(perm, ((Number) value).intValue());
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception ignored) {}
    }
}
