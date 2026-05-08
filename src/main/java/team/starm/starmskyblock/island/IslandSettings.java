package team.starm.starmskyblock.island;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

public class IslandSettings {

    private static final Gson GSON = new GsonBuilder().create();

    private static final Map<String, String> SETTING_KEYS = new LinkedHashMap<>();
    private static final Map<String, java.util.function.BiConsumer<IslandSettings, Boolean>> SETTING_SETTERS = new LinkedHashMap<>();
    private static final Map<String, java.util.function.Function<IslandSettings, Boolean>> SETTING_GETTERS = new LinkedHashMap<>();

    static {
        registerSetting("pvp", IslandSettings::setPvp, IslandSettings::isPvp);
        registerSetting("animal_spawn", IslandSettings::setAnimalSpawn, IslandSettings::isAnimalSpawn);
        registerSetting("monster_spawn", IslandSettings::setMonsterSpawn, IslandSettings::isMonsterSpawn);
        registerSetting("spawner_spawn", IslandSettings::setSpawnerSpawn, IslandSettings::isSpawnerSpawn);
        registerSetting("fire_spread", IslandSettings::setFireSpread, IslandSettings::isFireSpread);
        registerSetting("enderman_grief", IslandSettings::setEndermanGrief, IslandSettings::isEndermanGrief);
        registerSetting("ghast_fireball_grief", IslandSettings::setGhastFireballGrief, IslandSettings::isGhastFireballGrief);
        registerSetting("creeper_explosion", IslandSettings::setCreeperExplosion, IslandSettings::isCreeperExplosion);
        registerSetting("tnt_explosion", IslandSettings::setTntExplosion, IslandSettings::isTntExplosion);
        registerSetting("wither_grief", IslandSettings::setWitherGrief, IslandSettings::isWitherGrief);
    }

    private static void registerSetting(String key,
                                          java.util.function.BiConsumer<IslandSettings, Boolean> setter,
                                          java.util.function.Function<IslandSettings, Boolean> getter) {
        SETTING_KEYS.put(key, key);
        SETTING_SETTERS.put(key, setter);
        SETTING_GETTERS.put(key, getter);
    }

    private boolean pvp = true;
    private boolean animalSpawn = true;
    private boolean monsterSpawn = true;
    private boolean spawnerSpawn = true;
    private boolean fireSpread = true;
    private boolean endermanGrief = true;
    private boolean ghastFireballGrief = true;
    private boolean creeperExplosion = true;
    private boolean tntExplosion = true;
    private boolean witherGrief = true;

    public boolean isPvp() { return pvp; }
    public void setPvp(boolean pvp) { this.pvp = pvp; }

    public boolean isAnimalSpawn() { return animalSpawn; }
    public void setAnimalSpawn(boolean animalSpawn) { this.animalSpawn = animalSpawn; }

    public boolean isMonsterSpawn() { return monsterSpawn; }
    public void setMonsterSpawn(boolean monsterSpawn) { this.monsterSpawn = monsterSpawn; }

    public boolean isSpawnerSpawn() { return spawnerSpawn; }
    public void setSpawnerSpawn(boolean spawnerSpawn) { this.spawnerSpawn = spawnerSpawn; }

    public boolean isFireSpread() { return fireSpread; }
    public void setFireSpread(boolean fireSpread) { this.fireSpread = fireSpread; }

    public boolean isEndermanGrief() { return endermanGrief; }
    public void setEndermanGrief(boolean endermanGrief) { this.endermanGrief = endermanGrief; }

    public boolean isGhastFireballGrief() { return ghastFireballGrief; }
    public void setGhastFireballGrief(boolean ghastFireballGrief) { this.ghastFireballGrief = ghastFireballGrief; }

    public boolean isCreeperExplosion() { return creeperExplosion; }
    public void setCreeperExplosion(boolean creeperExplosion) { this.creeperExplosion = creeperExplosion; }

    public boolean isTntExplosion() { return tntExplosion; }
    public void setTntExplosion(boolean tntExplosion) { this.tntExplosion = tntExplosion; }

    public boolean isWitherGrief() { return witherGrief; }
    public void setWitherGrief(boolean witherGrief) { this.witherGrief = witherGrief; }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static IslandSettings fromJson(String json) {
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return new IslandSettings();
        }
        try {
            return GSON.fromJson(json, IslandSettings.class);
        } catch (Exception e) {
            return new IslandSettings();
        }
    }

    public static java.util.Set<String> getSettingKeys() {
        return SETTING_KEYS.keySet();
    }

    public boolean setByKey(String key, boolean value) {
        java.util.function.BiConsumer<IslandSettings, Boolean> setter = SETTING_SETTERS.get(key);
        if (setter == null) return false;
        setter.accept(this, value);
        return true;
    }

    public Boolean getByKey(String key) {
        java.util.function.Function<IslandSettings, Boolean> getter = SETTING_GETTERS.get(key);
        if (getter == null) return null;
        return getter.apply(this);
    }
}
