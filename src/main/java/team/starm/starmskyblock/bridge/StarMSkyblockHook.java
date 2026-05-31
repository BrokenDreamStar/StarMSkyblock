package team.starm.starmskyblock.bridge;

import me.arasple.mc.trmenu.module.internal.hook.HookAbstract;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import team.starm.starmskyblock.util.SkullManager;

/**
 * TrMenu JS 物品源桥接 Hook — 通过 {@code HookAbstract.bindingScript()} 自动注册到 JS 引擎。
 * <p>
 * 由于 TrMenu 的 {@code HookPlugin} 采用 TabooLib 的 {@code runningClasses} 扫描子类，
 * 在 StarMSkyblock 较晚加载的场景下可能无法自动发现，因此同时在
 * {@code StarMSkyblock.onEnable()} 中通过显式调用 {@code JavaScriptAgent.putBinding()} 确保注册。
 * <p>
 * TrMenu 菜单使用：
 * <pre>{@code
 * material: 'source:JS:StarMSkyblockAPI.getPlayerHead(player.getName())'
 * material: 'source:JS:StarMSkyblockAPI.getHeadByTexture("eyJ0ZXh0dXJlcyI6...")'
 * }</pre>
 */
public class StarMSkyblockHook extends HookAbstract {

    @Override
    public String getPluginName() {
        return "StarMSkyblock";
    }

    @Override
    public String getNamespace() {
        return "StarMSkyblockAPI";
    }

    /**
     * 获取指定玩家的头颅物品。
     * <p>
     * 优先检查纹理缓存，命中则直接构建带纹理头颅；
     * 未命中则异步获取纹理并缓存，当前调用返回无纹理头颅。
     *
     * @param playerName 玩家名称
     * @return 玩家头颅 ItemStack
     */
    public ItemStack getPlayerHead(String playerName) {
        return SkullManager.getPlayerHead(playerName);
    }

    /**
     * 解析岛屿列表占位符并获取岛主头颅。
     * <p>
     * 当 {@code value} 以 {@code "island_list_"} 开头时，会将其作为
     * {@code %starmskyblock_island_list_...%} 占位符通过 PlaceholderAPI 解析，
     * 将解析出的玩家名称传给 {@link #getPlayerHead(String)} 获取头颅。
     * <p>
     * 若解析返回空或 {@code "NONE"}（无效槽位），则返回默认无纹理头颅。
     * <p>
     * TrMenu 菜单使用：
     * <pre>{@code
     * material: 'source:JS:StarMSkyblockAPI.getPlayerHead(player, "island_list_1_owner")'
     * }</pre>
     *
     * @param player 菜单查看者（占位符上下文）
     * @param value  玩家名称或 {@code island_list_<slot>_owner} 占位符
     * @return 玩家头颅 ItemStack
     */
    public ItemStack getPlayerHead(Player player, String value) {
        if (value != null && value.startsWith("island_list_")) {
            String resolved = PlaceholderAPI.setPlaceholders(player, "%starmskyblock_" + value + "%");
            if (resolved != null && !resolved.isEmpty() && !resolved.equals("NONE")) {
                return SkullManager.getPlayerHead(resolved);
            }
            return new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        }
        return SkullManager.getPlayerHead(value);
    }

    /**
     * 直接用 base64 纹理字符串创建头颅物品。
     * 完全不触发 Mojang API，适用于已知纹理的场景。
     *
     * @param base64 Mojang textures property 的 base64 值
     * @return 带指定纹理的头颅 ItemStack
     */
    public ItemStack getHeadByTexture(String base64) {
        return SkullManager.getHeadWithTextures(base64);
    }
}
