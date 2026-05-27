package team.starm.starmskyblock.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class SkullTextureCache
{
    private static final Map<UUID, String> textureCache = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();

    public static String getTexture(UUID uuid) {
        if (uuid == null) return null;
        String cached = textureCache.get(uuid);
        if (cached != null) return cached;
        String texture = fetchFromPlayerProfile(uuid);
        if (texture == null) {
            texture = fetchFromMojangSession(uuid);
        }
        if (texture != null) {
            textureCache.put(uuid, texture);
        }
        return texture;
    }

    private static String fetchFromPlayerProfile(UUID uuid) {
        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            Object profile = offlinePlayer.getClass().getMethod("getPlayerProfile").invoke(offlinePlayer);
            Class<?> profileClass = profile.getClass();
            boolean complete = (boolean) profileClass.getMethod("isComplete").invoke(profile);
            if (!complete) {
                try {
                    profileClass.getMethod("complete", boolean.class, boolean.class).invoke(profile, true, false);
                } catch (Exception ignored) {}
            }
            Object properties = profileClass.getMethod("getProperties").invoke(profile);
            if (properties instanceof Iterable) {
                for (Object prop : (Iterable<?>) properties) {
                    String name = (String) prop.getClass().getMethod("getName").invoke(prop);
                    if ("textures".equals(name)) {
                        return (String) prop.getClass().getMethod("getValue").invoke(prop);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String fetchFromMojangSession(UUID uuid) {
        try {
            URI uri = new URI("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
            StringBuilder source = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(uri.toURL().openStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    source.append(line);
                }
            }
            JsonObject json = gson.fromJson(source.toString(), JsonObject.class);
            if (json != null && json.has("properties")) {
                JsonArray props = json.getAsJsonArray("properties");
                if (props.size() > 0) {
                    return props.get(0).getAsJsonObject().get("value").getAsString();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
