package team.starm.starmskyblock.listener;

import java.util.HashMap;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.world.SkyblockWorldManager;
import team.starm.starmskyblock.permission.BasePermissionManager;
import team.starm.starmskyblock.permission.IslandPermission;

/**
 * 黑曜石返还熔岩监听器
 * <p>
 * 继承 {@link BasePermissionManager}，监听玩家空手或持桶右键黑曜石的行为，
 * 在校验 BREAK 权限通过后将黑曜石还原为熔岩桶（消耗空桶）。
 * 用于解决玩家误用熔岩浇黑曜石后无法回收熔岩的问题。权限不足时取消事件并提示。
 * </p>
 */
public class ObsidianToLavaListener extends BasePermissionManager {

    public ObsidianToLavaListener(IslandManager islandManager, ConfigManager configManager,
                                    PublicAreaConfigManager publicAreaConfig,
                                    LockedAreaConfigManager lockedAreaConfig,
                                    JavaPlugin plugin, SkyblockWorldManager worldManager) {
        super(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
    }

    /**
     * 监听玩家右键方块事件
     * <p>
     * 当玩家手持空桶右键黑曜石时：先检查 BREAK 权限，通过则取消原版交互、
     * 消耗一个空桶、清除黑曜石并向玩家发放一个熔岩桶（背包满则掉落地面）。
     * 创造模式不消耗空桶。权限不足则取消事件并发送拒绝消息。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onObsidianClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack handItem = event.getItem();
        Block clickedBlock = event.getClickedBlock();

        if (handItem == null || clickedBlock == null) {
            return;
        }

        if (handItem.getType() != Material.BUCKET || clickedBlock.getType() != Material.OBSIDIAN) {
            return;
        }

        Location loc = clickedBlock.getLocation();

        if (!checkPermission(loc, player.getUniqueId(), IslandPermission.BREAK)) {
            event.setCancelled(true);
            event.setUseInteractedBlock(PlayerInteractEvent.Result.DENY);
            event.setUseItemInHand(PlayerInteractEvent.Result.DENY);
            sendDenyMessage(player, IslandPermission.BREAK);
            return;
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(PlayerInteractEvent.Result.DENY);
        event.setUseItemInHand(PlayerInteractEvent.Result.DENY);

        if (player.getGameMode() != GameMode.CREATIVE) {
            int amount = handItem.getAmount();
            if (amount > 1) {
                handItem.setAmount(amount - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }

        clickedBlock.setType(Material.AIR);

        ItemStack lavaBucket = new ItemStack(Material.LAVA_BUCKET);
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(lavaBucket);
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }
}
