package team.starm.starmskyblock.grid;

import team.starm.starmskyblock.config.ConfigManager;

public class GridManager {

    private final int gridCellSize; // 两个相邻岛屿中心之间的区块距离

    public GridManager(ConfigManager configManager) {
        // 网格单元大小 = 岛屿最大直径 (maxRadius * 2 + 1) + 间距 (spacing)
        this.gridCellSize = (configManager.getIslandMaxRadius() * 2) + 1 + configManager.getIslandSpacing();
    }

    /**
     * 根据岛屿的索引（ID）获取该岛屿所在的中心区块坐标（X，Z）。
     * 采用螺旋算法排列岛屿，避免长条状分布。
     *
     * @param index 岛屿ID（从 0 开始）
     * @return 包含中心区块坐标的 GridLocation 对象
     */
    public GridLocation getChunkLocation(int index) {
        // 数学推导的O(1)螺旋坐标计算 (Ulam Spiral)
        // 使用 1-based index 传入公式
        int n = index + 1;
        int k = (int) Math.ceil((Math.sqrt(n) - 1) / 2.0);
        int t = 2 * k;
        int m = (t + 1) * (t + 1);

        int x = 0;
        int z = 0;

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

    public int getGridCellSize() {
        return gridCellSize;
    }

    /**
     * 内部类用于存储中心区块坐标
     */
    public static class GridLocation {
        private final int chunkX;
        private final int chunkZ;

        public GridLocation(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        public int getChunkX() {
            return chunkX;
        }

        public int getChunkZ() {
            return chunkZ;
        }

        @Override
        public String toString() {
            return "Chunk(" + chunkX + ", " + chunkZ + ")";
        }
    }
}
