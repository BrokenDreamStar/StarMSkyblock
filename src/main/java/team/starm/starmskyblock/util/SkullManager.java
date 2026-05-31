package team.starm.starmskyblock.util;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.database.SQLiteManager;
import team.starm.starmskyblock.message.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * 玩家头颅管理器 — 纹理获取、缓存与头颅物品构建。
 * <p>
 * 设计完全参考 LiteSignIn 的 {@code SkullManager}：
 * <ul>
 *     <li>纹理缓存：{@code ConcurrentHashMap<UUID, String>}，避免重复请求 Mojang API</li>
 *     <li>纹理获取：优先从在线玩家 SkullMeta 提取，离线玩家直接请求 sessionserver.mojang.com</li>
 *     <li>头颅构建：GameProfile + 反射注入 SkullMeta.profile，兼容 1.21+ ResolvableProfile</li>
 * </ul>
 */
public class SkullManager {

    private static final Map<UUID, String> base64Meta = new ConcurrentHashMap<>();
    private static SQLiteManager database;
    private static Gson gson;

    private SkullManager() {
    }

    /**
     * 初始化数据库引用并从 DB 加载所有已持久化的纹理到缓存。
     * 应在服务器启动时调用（onEnable）。
     */
    public static void initDatabase(SQLiteManager sqliteManager) {
        database = sqliteManager;
        Map<UUID, String> textures = database.loadAllSkinTextures();
        base64Meta.putAll(textures);
        MessageUtil.consolePrint("已预加载 " + textures.size() + " 个皮肤纹理到缓存");
    }

    // ==================== 纹理获取 ====================

    /**
     * 直接请求 Mojang session server 获取玩家皮肤纹理并缓存。
     * <p>
     * 若已缓存或为离线模式玩家则跳过。
     *
     * @param uuid 玩家 UUID
     * @param name 玩家名称
     */
    public static void refreshTexture(UUID uuid, String name) {
        if (gson == null) {
            gson = new Gson();
        }
        if (base64Meta.containsKey(uuid)) return;
        if (UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()).equals(uuid)) {
            return;
        }
        StringBuilder source = new StringBuilder();
        try {
            URL url = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", "")).toURL();
            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"))) {
                while ((line = reader.readLine()) != null) {
                    source.append(line);
                    source.append('\n');
                }
            }
            JsonObject json = gson.fromJson(source.toString(), JsonObject.class);
            String texture = json.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
            base64Meta.put(uuid, texture);
            if (database != null) {
                database.saveSkinTexture(uuid, texture);
            }
        } catch (Exception e) {
            MessageUtil.consoleWarn("获取玩家 " + name + " 的皮肤纹理失败: " + e.getMessage());
        }
    }

    

    // ==================== 纹理提取 ====================

    /**
     * 从头颅 ItemStack 的 SkullMeta 中提取 skin texture base64 值。
     * 兼容 GameProfile / ResolvableProfile / PlayerProfile 三种 profile 类型。
     *
     * @param headItem 头颅 ItemStack
     * @return base64 纹理字符串，提取失败返回 null
     */
    public static String getHeadTexturesFromHead(ItemStack headItem) {
        if (headItem == null) return null;
        if (headItem.getItemMeta() instanceof SkullMeta) {
            SkullMeta skull = (SkullMeta) headItem.getItemMeta();
            Field profileField;
            try {
                profileField = skull.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                GameProfile profile;
                try {
                    profile = (GameProfile) profileField.get(skull);
                } catch (ClassCastException ex) {
                    Object resolvableProfile = profileField.get(skull);
                    try {
                        profile = (GameProfile) resolvableProfile.getClass().getMethod("gameProfile").invoke(resolvableProfile);
                    } catch (NoSuchMethodException ex1) {
                        Method method_ = Arrays.stream(resolvableProfile.getClass().getMethods())
                                .filter(method -> method.getReturnType().getName().equals("com.mojang.authlib.GameProfile") && method.getParameterTypes().length == 0)
                                .findFirst().orElse(null);
                        profile = method_ == null ? null : (GameProfile) method_.invoke(resolvableProfile);
                    }
                }
                if (profile != null) {
                    Property property = getProperties(profile).get("textures").stream().findFirst().orElse(null);
                    if (property != null) {
                        try {
                            return property.value();
                        } catch (NoSuchMethodError error) {
                            try {
                                return (String) property.getClass().getMethod("getValue").invoke(property);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                return null;
            }
        }
        return null;
    }

    // ==================== 头颅构建 ====================

    /**
     * 用 base64 纹理创建玩家头颅（GameProfile + 反射注入）。
     *
     * @param textures base64 纹理字符串
     * @return 带正确皮肤纹理的头颅 ItemStack
     */
    public static ItemStack getHeadWithTextures(String textures) {
        ItemStack headItem = new ItemStack(Material.PLAYER_HEAD);
        if (textures == null) return headItem;
        SkullMeta skull = (SkullMeta) headItem.getItemMeta();
        GameProfile profile = generateGameProfile(textures);
        Field profileField;
        try {
            profileField = skull.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            try {
                profileField.set(skull, profile);
            } catch (IllegalArgumentException ex) {
                try {
                    Object resolvableProfile = Class.forName("net.minecraft.world.item.component.ResolvableProfile")
                            .getConstructor(GameProfile.class).newInstance(profile);
                    profileField.set(skull, resolvableProfile);
                } catch (NoSuchMethodException ex1) {
                    profileField.set(skull, Class.forName("net.minecraft.world.item.component.ResolvableProfile")
                            .getMethod("createResolved", GameProfile.class).invoke(null, profile));
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        headItem.setItemMeta(skull);
        return headItem;
    }

    /**
     * 获取指定玩家的头颅物品。
     * <p>
     * 统一使用 {@code Bukkit.getOfflinePlayer(name).getUniqueId()} 作为缓存键，
     * 消除在线/离线路径 UUID 不一致导致的正版离线玩家纹理永久丢失问题。
     * <p>
     * 非正版玩家（UUID 与离线模式计算结果一致）直接返回默认 {@code PLAYER_HEAD}，不发起任何请求。
     * <p>
     * 正版玩家缓存命中时直接返回纹理头颅；未命中时异步触发 {@link #refreshTexture(UUID, String)}
     * 预热缓存（纯 HTTP，不阻塞），同时立即返回（在线玩家带 owner 引用，离线玩家空白头）。
     *
     * @param playerName 玩家名称
     * @return 玩家头颅 ItemStack
     */
    public static ItemStack getPlayerHead(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return new ItemStack(Material.PLAYER_HEAD);
        }

        Player online = Bukkit.getPlayerExact(playerName);

        if (online != null) {
            UUID uuid = online.getUniqueId();
            String texture = base64Meta.get(uuid);
            if (texture != null) {
                return getHeadWithTextures(texture);
            }

            final UUID finalUuid = uuid;
            final String finalName = online.getName();
            Bukkit.getScheduler().runTaskAsynchronously(
                    Bukkit.getPluginManager().getPlugin("StarMSkyblock"),
                    () -> refreshTexture(finalUuid, finalName));

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(online);
            head.setItemMeta(meta);
            return head;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = offlinePlayer.getUniqueId();

        UUID offlineModeId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes());
        if (offlineModeId.equals(uuid)) {
            return new ItemStack(Material.PLAYER_HEAD);
        }

        String texture = base64Meta.get(uuid);
        if (texture != null) {
            return getHeadWithTextures(texture);
        }

        Bukkit.getScheduler().runTaskAsynchronously(
                Bukkit.getPluginManager().getPlugin("StarMSkyblock"),
                () -> refreshTexture(uuid, playerName));
        return new ItemStack(Material.PLAYER_HEAD);
    }

    // ==================== GameProfile 工具 ====================

    /**
     * 创建带纹理属性的 GameProfile。
     * 兼容 1.21.9+ PropertyMap 构造变更。
     *
     * @param textures base64 纹理字符串
     * @return GameProfile 实例
     */
    public static GameProfile generateGameProfile(String textures) {
        try {
            GameProfile profile = new GameProfile(UUID.randomUUID(), "Skull");
            profile.getProperties().put("textures", new Property("textures", textures));
            return profile;
        } catch (NoSuchMethodError error) {
            try {
                Multimap<String, Property> map = ArrayListMultimap.create();
                map.put("textures", new Property("textures", textures));
                PropertyMap propertyMap = PropertyMap.class.getConstructor(Multimap.class).newInstance(map);
                return GameProfile.class.getConstructor(UUID.class, String.class, PropertyMap.class)
                        .newInstance(UUID.randomUUID(), "Skull", propertyMap);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 跨版本获取 GameProfile 的 PropertyMap。
     * 兼容 1.21.9+ 方法名变更（getProperties → properties）。
     *
     * @param profile GameProfile 实例
     * @return PropertyMap
     */
    public static PropertyMap getProperties(GameProfile profile) {
        try {
            return profile.getProperties();
        } catch (NoSuchMethodError error) {
            try {
                return (PropertyMap) profile.getClass().getMethod("properties").invoke(profile);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 获取纹理缓存（用于外部预热或调试）。
     *
     * @return UUID → base64 纹理映射
     */
    public static Map<UUID, String> getBase64Meta() {
        return base64Meta;
    }
}
