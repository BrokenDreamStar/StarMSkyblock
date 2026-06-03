package team.starm.starmskyblock.generator;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * 刷石机生成器类型检测。
 * 仅检测玄武岩生成场景（熔岩 + 灵魂土 + 蓝冰）。
 * 圆石 / 石头生成由 CobblestoneGeneratorListener 直接处理。
 */
public enum GeneratorType {

    /** 岩浆 + 蓝冰 + 灵魂土 → 玄武岩（下界） */
    BASALT(Material.BASALT),

    /** 非生成器场景 */
    NONE(null);

    private static final BlockFace[] BASALT_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP
    };

    private final Material defaultBlock;

    GeneratorType(Material defaultBlock) {
        this.defaultBlock = defaultBlock;
    }

    /**
     * 检测目标位置是否处于玄武岩生成场景。
     *
     * @param block 目标方块（BlockFromToEvent 的 toBlock）
     * @return 生成器类型
     */
    public static GeneratorType detect(Block block) {
        if (block.getRelative(BlockFace.DOWN).getType() == Material.SOUL_SOIL) {
            for (BlockFace face : BASALT_FACES) {
                if (block.getRelative(face).getType() == Material.BLUE_ICE) {
                    return BASALT;
                }
            }
        }
        return NONE;
    }
}
