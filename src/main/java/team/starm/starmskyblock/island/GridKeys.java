package team.starm.starmskyblock.island;

/**
 * 网格键编码工具
 * <p>
 * 将两个 int 坐标（网格 cell 坐标或 chunk 坐标）编码为单个 long 键，
 * 供 {@link IslandManager#islandGridIndex} 等 Map 做区块 -> 岛屿 O(1) 反向查询。
 * </p>
 * <p>
 * 编码方式：高位 32 bit 存 x，低位 32 bit 存 z。{@code & 0xffffffffL} 保证负数 z
 * 的高位不会因符号扩展污染 x 的高 32 bit（z 视为无符号 32 位写入低位）。
 * </p>
 */
public final class GridKeys {

    private GridKeys() {
    }

    /**
     * 将两个 int 坐标编码为 long 网格键：{@code (x << 32) | (z & 0xffffffffL)}。
     *
     * @param x 第一个坐标（网格 x 或 chunk x）
     * @param z 第二个坐标（网格 z 或 chunk z）
     * @return 编码后的 long 键
     */
    public static long encode(int x, int z) {
        return (((long) x) << 32) | (z & 0xffffffffL);
    }
}
