package team.starm.starmskyblock.grid;

/**
 * Ulam 螺旋（整数螺旋）坐标计算。
 * <p>
 * 将 0-based 索引映射为螺旋上的网格单元偏移 (gridX, gridZ)，使索引越小越靠近原点。
 * O(1) 闭式计算。原内联于 {@link GridManager#getChunkLocation(int)}，抽出为纯工具类以便单元测试。
 * 无 Bukkit 依赖。
 */
public final class UlamSpiral {

    private UlamSpiral() {
    }

    /**
     * 返回索引在螺旋上的网格单元偏移（不含 cellSize 缩放）。
     *
     * @param index 0-based 索引
     * @return {@code int[2]}，{gridX, gridZ}
     */
    public static int[] spiralOffset(int index) {
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
        return new int[]{x, z};
    }
}
