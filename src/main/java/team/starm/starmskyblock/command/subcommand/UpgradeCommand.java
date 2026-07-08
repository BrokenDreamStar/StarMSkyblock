package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.UpgradeConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.message.MessageUtil;
import net.milkbowl.vault.economy.Economy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UpgradeCommand extends SubCommand {

    public UpgradeCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            MessageUtil.send(player, "upgrade.economy-not-loaded");
            return true;
        }

        Optional<Island> optional = plugin.getIslandManager().getIsland(player.getUniqueId());
        if (optional.isEmpty()) {
            MessageUtil.send(player, "upgrade.no-island");
            return true;
        }
        Island island = optional.get();

        if (!island.getOwnerId().equals(player.getUniqueId())) {
            MessageUtil.send(player, "upgrade.owner-only");
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

    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return List.of("radius", "generator").stream()
                    .filter(v -> v.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private void showUpgradeInfo(Player player, Island island, Economy economy) {
        int maxRadius = plugin.getConfigManager().getIslandMaxRadius();
        int genMaxLevel = plugin.getGeneratorConfigManager().getMaxLevel();

        MessageUtil.send(player, "upgrade.info.header");
        if (island.getRadius() >= maxRadius) {
            MessageUtil.send(player, "upgrade.info.radius-current-max", Map.of("radius", island.getRadius()));
        } else {
            MessageUtil.send(player, "upgrade.info.radius-current", Map.of("radius", island.getRadius()));
        }
        if (island.getGeneratorLevel() >= genMaxLevel) {
            MessageUtil.send(player, "upgrade.info.generator-current-max", Map.of("level", island.getGeneratorLevel()));
        } else {
            MessageUtil.send(player, "upgrade.info.generator-current", Map.of("level", island.getGeneratorLevel()));
        }

        UpgradeConfigManager upgradeConfig = plugin.getUpgradeConfigManager();

        if (island.getRadius() < maxRadius) {
            upgradeConfig.getNextRadiusUpgrade(island.getRadius()).ifPresentOrElse(
                    next -> MessageUtil.send(player, "upgrade.info.radius-next",
                            Map.of("radius", next.radius(), "cost", economy.format(next.money()))),
                    () -> MessageUtil.send(player, "upgrade.info.radius-next-none"));
        }

        if (island.getGeneratorLevel() < genMaxLevel) {
            upgradeConfig.getNextGeneratorUpgrade(island.getGeneratorLevel()).ifPresentOrElse(
                    next -> MessageUtil.send(player, "upgrade.info.generator-next",
                            Map.of("level", next.generatorLevel(), "cost", economy.format(next.money()))),
                    () -> MessageUtil.send(player, "upgrade.info.generator-next-none"));
        }

        MessageUtil.send(player, "upgrade.usage.radius");
        MessageUtil.send(player, "upgrade.usage.generator");
    }

    private void handleRadiusUpgrade(Player player, Island island, Economy economy) {
        int maxRadius = plugin.getConfigManager().getIslandMaxRadius();
        if (island.getRadius() >= maxRadius) {
            MessageUtil.send(player, "upgrade.radius.max-reached");
            return;
        }

        UpgradeConfigManager upgradeConfig = plugin.getUpgradeConfigManager();
        Optional<UpgradeConfigManager.RadiusUpgrade> next = upgradeConfig.getNextRadiusUpgrade(island.getRadius());

        if (next.isEmpty()) {
            MessageUtil.send(player, "upgrade.radius.config-not-found");
            return;
        }

        UpgradeConfigManager.RadiusUpgrade upgrade = next.get();

        if (upgrade.radius() > maxRadius) {
            MessageUtil.send(player, "upgrade.target-exceeds-max");
            return;
        }

        if (!economy.has(player, upgrade.money())) {
            MessageUtil.send(player, "upgrade.insufficient-funds", Map.of("cost", economy.format(upgrade.money())));
            return;
        }

        economy.withdrawPlayer(player, upgrade.money());
        plugin.getIslandManager().updateIslandRadius(island.getId(), upgrade.radius());
        MessageUtil.send(player, "upgrade.radius.success", Map.of("radius", upgrade.radius()));
    }

    private void handleGeneratorUpgrade(Player player, Island island, Economy economy) {
        int genMaxLevel = plugin.getGeneratorConfigManager().getMaxLevel();
        if (island.getGeneratorLevel() >= genMaxLevel) {
            MessageUtil.send(player, "upgrade.generator.max-reached");
            return;
        }

        UpgradeConfigManager upgradeConfig = plugin.getUpgradeConfigManager();
        Optional<UpgradeConfigManager.GeneratorUpgrade> next = upgradeConfig.getNextGeneratorUpgrade(island.getGeneratorLevel());

        if (next.isEmpty()) {
            MessageUtil.send(player, "upgrade.generator.config-not-found");
            return;
        }

        UpgradeConfigManager.GeneratorUpgrade upgrade = next.get();

        if (upgrade.generatorLevel() > genMaxLevel) {
            MessageUtil.send(player, "upgrade.target-exceeds-max");
            return;
        }

        if (!economy.has(player, upgrade.money())) {
            MessageUtil.send(player, "upgrade.insufficient-funds", Map.of("cost", economy.format(upgrade.money())));
            return;
        }

        economy.withdrawPlayer(player, upgrade.money());
        plugin.getIslandManager().updateIslandGeneratorLevel(island.getId(), upgrade.generatorLevel());
        MessageUtil.send(player, "upgrade.generator.success", Map.of("level", upgrade.generatorLevel()));
    }
}
