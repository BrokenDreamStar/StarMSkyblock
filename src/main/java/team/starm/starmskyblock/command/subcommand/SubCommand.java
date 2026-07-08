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

public abstract class SubCommand {

    protected final StarMSkyblock plugin;

    public SubCommand(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public abstract boolean execute(Player player, String[] args);

    public List<String> onTabComplete(Player player, String[] args) {
        return Collections.emptyList();
    }

    protected Optional<Island> getIsland(Player player) {
        return plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
    }

    protected String getPlayerName(UUID uuid) {
        Optional<String> dbName = plugin.getPlayerRepo().getPlayerName(uuid);
        return dbName.orElse(uuid.toString());
    }

    protected boolean isLocationSafe(org.bukkit.Location location) {
        org.bukkit.Location blockLoc = location.clone();
        org.bukkit.Location blockBelow = blockLoc.clone().subtract(0, 1, 0);
        org.bukkit.block.Block footBlock = blockLoc.getBlock();
        org.bukkit.block.Block belowBlock = blockBelow.getBlock();

        if (footBlock.isLiquid() || belowBlock.isLiquid()) return false;
        if (belowBlock.getType().isAir()) return false;
        org.bukkit.block.Block aboveBlock = blockLoc.clone().add(0, 1, 0).getBlock();
        if (aboveBlock.getType().isSolid()) return false;
        return true;
    }

    protected boolean assertMaxArgs(Player player, String[] args, int max, String usage) {
        if (args.length > max) {
            MessageUtil.send(player, "general.usage", Map.of("usage", usage));
            return false;
        }
        return true;
    }
}
