package team.starm.starmskyblock.util;

import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.util.Objects;

public class EntityUtil {
    /**
     * 判断马/骷髅马/僵尸马/驴/猪/炽足兽/骆驼/骆驼尸壳/鹦鹉螺/僵尸鹦鹉螺是否装备了鞍
     */
    public static boolean hasSaddle(LivingEntity entity) {
        if (entity == null)
            return false;

        // 马/骷髅马/僵尸马/驴/骆驼/骆驼尸壳
        if (entity instanceof AbstractHorse abstractHorse) {
            ItemStack saddle = abstractHorse.getInventory().getSaddle();
            if (saddle != null && !saddle.isEmpty())
                return true;
        }

        // 猪/炽足兽
        if (entity instanceof Steerable steerable) {
            if (steerable.hasSaddle())
                return true;
        }

        // 鹦鹉螺/僵尸鹦鹉螺
        if (entity instanceof AbstractNautilus abstractNautilus) {
            ItemStack saddle = abstractNautilus.getInventory().getSaddle();
            if (saddle != null && !saddle.isEmpty())
                return true;
        }

        // 鞍槽位是否有鞍
        ItemStack saddleItem = Objects.requireNonNull(entity.getEquipment()).getItem(EquipmentSlot.valueOf("SADDLE"));
        return !saddleItem.isEmpty() && saddleItem.getType() == Material.SADDLE;
    }
}