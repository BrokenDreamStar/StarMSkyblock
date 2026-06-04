package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.UpgradeConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.message.MessageUtil;
import net.milkbowl.vault.economy.Economy;

import java.util.Optional;

public class UpgradeCommand extends SubCommand {

    public UpgradeCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            MessageUtil.sendMessage(player, "&c经济系统未接入，无法使用升级功能！");
            return true;
        }

        Optional<Island> optional = plugin.getIslandManager().getIsland(player.getUniqueId());
        if (optional.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你没有岛屿！");
            return true;
        }
        Island island = optional.get();

        if (!island.getOwnerId().equals(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&c只有岛主才能升级岛屿！");
            return true;
        }

        if (args.length < 2) {
            showUpgradeInfo(player, island, economy);
            return true;
        }

        String type = args[1].toLowerCase();
        switch (type) {
            case "radius" -> handleRadiusUpgrade(player, island, economy);
            case "generator" -> handleGeneratorUpgrade(player, island, economy);
            default -> showUpgradeInfo(player, island, economy);
        }
        return true;
    }

    private void showUpgradeInfo(Player player, Island island, Economy economy) {
        int maxRadius = plugin.getConfigManager().getIslandMaxRadius();
        int genMaxLevel = plugin.getGeneratorConfigManager().getMaxLevel();

        MessageUtil.sendMessage(player, "&a=== 岛屿升级信息 ===");
        MessageUtil.sendMessage(player, "&7当前岛屿范围: &e" + island.getRadius() + " &7区块" +
                (island.getRadius() >= maxRadius ? " &c(已达最大)" : ""));
        MessageUtil.sendMessage(player, "&7当前刷石机等级: &e" + island.getGeneratorLevel() +
                (island.getGeneratorLevel() >= genMaxLevel ? " &c(已达最大)" : ""));

        UpgradeConfigManager upgradeConfig = plugin.getUpgradeConfigManager();

        if (island.getRadius() < maxRadius) {
            upgradeConfig.getNextRadiusUpgrade(island.getRadius()).ifPresentOrElse(
                    next -> MessageUtil.sendMessage(player, "&7下个范围等级: &e" + next.radius() +
                            " &7区块 - 费用: &6" + economy.format(next.money())),
                    () -> MessageUtil.sendMessage(player, "&7下个范围等级: &c无更多配置"));
        }

        if (island.getGeneratorLevel() < genMaxLevel) {
            upgradeConfig.getNextGeneratorUpgrade(island.getGeneratorLevel()).ifPresentOrElse(
                    next -> MessageUtil.sendMessage(player, "&7下个刷石机等级: &e" + next.generatorLevel() +
                            " &7- 费用: &6" + economy.format(next.money())),
                    () -> MessageUtil.sendMessage(player, "&7下个刷石机等级: &c无更多配置"));
        }

        MessageUtil.sendMessage(player, "&a/is upgrade radius &f- 升级岛屿范围");
        MessageUtil.sendMessage(player, "&a/is upgrade generator &f- 升级刷石机");
    }

    private void handleRadiusUpgrade(Player player, Island island, Economy economy) {
        int maxRadius = plugin.getConfigManager().getIslandMaxRadius();
        if (island.getRadius() >= maxRadius) {
            MessageUtil.sendMessage(player, "&c岛屿范围已达最大等级！");
            return;
        }

        UpgradeConfigManager upgradeConfig = plugin.getUpgradeConfigManager();
        Optional<UpgradeConfigManager.RadiusUpgrade> next = upgradeConfig.getNextRadiusUpgrade(island.getRadius());

        if (next.isEmpty()) {
            MessageUtil.sendMessage(player, "&c未找到下一级范围升级配置！");
            return;
        }

        UpgradeConfigManager.RadiusUpgrade upgrade = next.get();

        if (upgrade.radius() > maxRadius) {
            MessageUtil.sendMessage(player, "&c升级目标超过服务器最大限制！");
            return;
        }

        if (!economy.has(player, upgrade.money())) {
            MessageUtil.sendMessage(player, "&c余额不足！需要 &6" + economy.format(upgrade.money()));
            return;
        }

        economy.withdrawPlayer(player, upgrade.money());
        plugin.getIslandManager().updateIslandRadius(island.getId(), upgrade.radius());
        MessageUtil.sendMessage(player, "&a岛屿范围已升级至 &e" + upgrade.radius() + " &a区块！");
    }

    private void handleGeneratorUpgrade(Player player, Island island, Economy economy) {
        int genMaxLevel = plugin.getGeneratorConfigManager().getMaxLevel();
        if (island.getGeneratorLevel() >= genMaxLevel) {
            MessageUtil.sendMessage(player, "&c刷石机已达最大等级！");
            return;
        }

        UpgradeConfigManager upgradeConfig = plugin.getUpgradeConfigManager();
        Optional<UpgradeConfigManager.GeneratorUpgrade> next = upgradeConfig.getNextGeneratorUpgrade(island.getGeneratorLevel());

        if (next.isEmpty()) {
            MessageUtil.sendMessage(player, "&c未找到下一级刷石机升级配置！");
            return;
        }

        UpgradeConfigManager.GeneratorUpgrade upgrade = next.get();

        if (upgrade.generatorLevel() > genMaxLevel) {
            MessageUtil.sendMessage(player, "&c升级目标超过服务器最大限制！");
            return;
        }

        if (!economy.has(player, upgrade.money())) {
            MessageUtil.sendMessage(player, "&c余额不足！需要 &6" + economy.format(upgrade.money()));
            return;
        }

        economy.withdrawPlayer(player, upgrade.money());
        plugin.getIslandManager().updateIslandGeneratorLevel(island.getId(), upgrade.generatorLevel());
        MessageUtil.sendMessage(player, "&a刷石机已升级至 &e" + upgrade.generatorLevel() + " &a级！");
    }
}
