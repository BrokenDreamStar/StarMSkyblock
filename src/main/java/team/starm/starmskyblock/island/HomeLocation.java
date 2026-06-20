package team.starm.starmskyblock.island;

import com.google.gson.Gson;

/**
 * 自定义传送点的可序列化载体。
 * <p>
 * 取代原本手拼 JSON 字符串的做法 —— 手拼方式无法处理 NaN/Infinity，
 * 且字段顺序/拼写散落两处（{@code Island.setCustomHome} 与
 * {@code IslandManager.updateIslandCustomHome}）。统一走 Gson 后
 * 两端共享同一份序列化契约。
 */
public final class HomeLocation {

    private static final Gson GSON = new Gson();

    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public HomeLocation(Island.WorldType worldType, double x, double y, double z, float yaw, float pitch) {
        this.world = worldType.name();
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static String toJson(Island.WorldType worldType, double x, double y, double z, float yaw, float pitch) {
        return GSON.toJson(new HomeLocation(worldType, x, y, z, yaw, pitch));
    }

    /** 反序列化；非合法 JSON 或缺字段时返回 null。 */
    public static HomeLocation fromJson(String json) {
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return null;
        }
        try {
            HomeLocation loc = GSON.fromJson(json, HomeLocation.class);
            if (loc == null || loc.world == null) {
                return null;
            }
            return loc;
        } catch (Exception e) {
            return null;
        }
    }

    public Island.WorldType getWorldType() {
        return Island.WorldType.valueOf(world);
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
}
