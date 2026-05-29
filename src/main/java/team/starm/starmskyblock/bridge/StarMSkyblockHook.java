package team.starm.starmskyblock.bridge;

import me.arasple.mc.trmenu.module.internal.hook.HookAbstract;
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
