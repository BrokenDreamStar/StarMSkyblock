package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * 玩家子命令抽象基类
 * <p>
 * 所有 {@code /is} 子命令的公共父类，持有插件主类引用并提供岛屿查询、
 * 玩家名解析、位置安全判定、参数数量校验等通用工具方法。
 * 具体子命令实现 {@link #execute(Player, String[])} 即可。
 */
public abstract class SubCommand {

    protected final StarMSkyblock plugin;

    public SubCommand(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * 执行子命令。
     *
     * @param player 执行命令的玩家
     * @param args   去除主命令名后的参数数组（args[0] 为子命令名）
     * @return true 表示已处理
     */
    public abstract boolean execute(Player player, String[] args);

    /**
     * 子命令 Tab 补全，默认返回空列表。子类按需重写。
     */
    public List<String> onTabComplete(Player player, String[] args) {
        return Collections.emptyList();
    }

    /**
     * 查询玩家所属岛屿（按 owner/member 索引）。
     */
    protected Optional<Island> getIsland(Player player) {
        return plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
    }

    /**
     * 根据 UUID 解析玩家名：优先查数据库缓存，查不到回退为 UUID 字符串。
     */
    protected String getPlayerName(UUID uuid) {
        Optional<String> dbName = plugin.getPlayerRepo().getPlayerName(uuid);
        return dbName.orElse(uuid.toString());
    }

    /**
     * 判定目标位置是否可安全传送：脚下方块和其下方方块均非液体、下方非空气、
     * 头顶非实心方块，避免传送到岩浆/水里或被卡进方块。
     */
    protected boolean isLocationSafe(Location location) {
        Location blockLoc = location.clone();
        Location blockBelow = blockLoc.clone().subtract(0, 1, 0);
        Block footBlock = blockLoc.getBlock();
        Block belowBlock = blockBelow.getBlock();

        if (footBlock.isLiquid() || belowBlock.isLiquid()) return false;
        if (belowBlock.getType().isAir()) return false;
        Block aboveBlock = blockLoc.clone().add(0, 1, 0).getBlock();
        if (aboveBlock.getType().isSolid()) return false;
        return true;
    }

    /**
     * 校验参数数量不超过上限，超限时向玩家发送用法提示并返回 false。
     *
     * @param max   允许的最大参数个数（不含子命令名）
     * @param usage 用法字符串，展示给玩家
     * @return true 表示参数数量合法
     */
    protected boolean assertMaxArgs(Player player, String[] args, int max, String usage) {
        if (args.length > max) {
            MessageUtil.send(player, "general.usage", Map.of("usage", usage));
            return false;
        }
        return true;
    }
}
