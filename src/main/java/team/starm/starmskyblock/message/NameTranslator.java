package team.starm.starmskyblock.message;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * 材质/实体名 -> 客户端可翻译组件转换器。
 * <p>
 * 将 Bukkit 的 {@link Material}/{@link EntityType} 枚举名（SCREAMING_SNAKE_CASE）解析为
 * Adventure {@link Component#translatable(String)}，由客户端按自身语言设置渲染，
 * 从而避免在服务端为每种语言维护翻译表。解析失败时回退为纯文本组件。
 */
public final class NameTranslator {

    private NameTranslator() {}

    private static final String MINECRAFT = "minecraft";

    /**
     * 将 Material/EntityType 枚举名（SCREAMING_SNAKE_CASE）转换为客户端语言对应的可翻译组件。
     * 优先解析为 Material，其次为 EntityType，均失败则回退为纯文本。
     */
    public static @NotNull Component translatable(@NotNull String typeName) {
        Material mat = Material.matchMaterial(typeName);
        if (mat != null) {
            String path = mat.name().toLowerCase(Locale.ROOT);
            String prefix = mat.isBlock() ? "block." : "item.";
            return Component.translatable(prefix + MINECRAFT + "." + path);
        }

        try {
            EntityType entityType = EntityType.valueOf(typeName.toUpperCase(Locale.ROOT));
            String path = entityType.name().toLowerCase(Locale.ROOT);
            return Component.translatable("entity." + MINECRAFT + "." + path);
        } catch (IllegalArgumentException e) {
            return Component.text(typeName);
        }
    }
}
