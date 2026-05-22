package team.starm.starmskyblock.grid;

import team.starm.starmskyblock.config.ConfigManager;

/**
 * 网格管理器 —— 以螺旋（Ulam Spiral）算法将岛屿 ID 映射为世界中的区块坐标。
 * <p>
 * 每个岛屿占据一个网格单元（grid cell），单元尺寸由最大半径和间距共同决定。
 * 这种布局保证岛屿之间留有足够的缓冲空间，且所有岛屿以中心原点向外螺旋排列，
 * 而非简单的长条状分布。
 */
public class GridManager {

    /** 单个网格单元的边长（单位：区块数） */
    private final int gridCellSize;

    public GridManager(ConfigManager configManager) {
        // 网格单元大小 = 岛屿最大直径 (maxRadius * 2 + 1) + 间距 (spacing)
        this.gridCellSize = (configManager.getIslandMaxRadius() * 2) + 1 + configManager.getIslandSpacing();
    }

    /**
     * 根据岛屿的索引（ID）获取该岛屿所在的中心区块坐标（X，Z）。
     * 采用 Ulam Spiral 算法排列岛屿，使 ID 越小的岛屿越靠近世界原点。
     *
     * @param index 岛屿ID（从 0 开始）
     * @return 包含中心区块坐标的 GridLocation 对象
     */
    public GridLocation getChunkLocation(int index) {
        // 数学推导的 O(1) 螺旋坐标计算 (Ulam Spiral)
        // 使用 1-based index 传入公式
        int n = index + 1;
        int k = (int) Math.ceil((Math.sqrt(n) - 1) / 2.0);
        int t = 2 * k;
        int m = (t + 1) * (t + 1);

        int x = 0;
        int z = 0;

        // 根据 n 在螺旋上的位置确定 (x, z)
        if (n >= m - t) {
            x = k - (m - n);
            z = -k;
        } else {
            m -= t;
            if (n >= m - t) {
                x = -k;
                z = -k + (m - n);
            } else {
                m -= t;
                if (n >= m - t) {
                    x = -k + (m - n);
                    z = k;
                } else {
                    x = k;
                    z = k - (m - n - t);
                }
            }
        }

        // 计算出网格坐标后，乘以单个网格的尺寸，得到中心区块的坐标
        return new GridLocation(x * gridCellSize, z * gridCellSize);
    }

    /** 获取网格单元大小（区块数） */
    public int getGridCellSize() {
        return gridCellSize;
    }

    /** 岛屿中心区块坐标的不可变值对象 */
    public record GridLocation(int chunkX, int chunkZ) {
        @Override
        public String toString() {
            return "Chunk(%d, %d)".formatted(chunkX, chunkZ);
        }
    }
}
