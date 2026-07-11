package team.starm.starmskyblock.util;

/**
 * 方块/区块坐标的位打包键编解码。
 * <p>
 * 原内联于 {@code BlockBreakTaskListener} 的私有静态方法，抽出为纯工具类以便单元测试与复用。
 * 无 Bukkit 依赖。
 * <p>
 * 两种编码（均装入 long）：
 * <ul>
 *   <li>区块键：16-bit worldIndex | 16-bit chunkX | 16-bit chunkZ（共 48 位）</li>
 *   <li>方块键：4-bit x | 10-bit (y+128) | 4-bit z</li>
 * </ul>
 * 注意：本编码与 {@code island.GridKeys}（岛屿网格单元键）语义不同，不可合并。
 */
public final class BlockCoordKeys {

    private BlockCoordKeys() {
    }

    /**
     * 将世界索引 + 区块坐标编码为 48-bit long 复合键。
     *
     * @param worldIndex 世界索引（必须 0..65535，否则抛 IllegalStateException）
     * @param cx         区块 X（有效范围 ±32767，超出会被掩码截断）
     * @param cz         区块 Z（有效范围 ±32767）
     * @return 48-bit 复合键
     */
    public static long encodeChunkKey(int worldIndex, int cx, int cz) {
        if ((worldIndex & 0xFFFF) != worldIndex) {
            throw new IllegalStateException("worldIndex exceeds 16 bits: " + worldIndex);
        }
        // 16-bit worldIndex | 16-bit chunkX | 16-bit chunkZ = 48 bits, fits in a long.
        // chunkX/Z cover ±32767 chunks = ±524272 blocks, far beyond island sizes.
        return ((long) (worldIndex & 0xFFFF) << 32)
             | ((long) (cx & 0xFFFF) << 16)
             | (cz & 0xFFFFL);
    }

    /**
     * 将区块内方块坐标编码为 long 键：4-bit x | 10-bit (y+128) | 4-bit z。
     *
     * @param x 区块内 X（0..15）
     * @param y 世界 Y（-128..895，内部 +128 落入 0..1023）
     * @param z 区块内 Z（0..15）
     * @return 方块键
     */
    public static long encodeBlockKey(int x, int y, int z) {
        int yi = y + 128;
        return ((long) (x & 0xF) << 14)
             | ((long) (yi & 0x3FF) << 4)
             | (z & 0xF);
    }

    public static int decodeWorldIndex(long key) {
        return (int) ((key >>> 32) & 0xFFFF);
    }

    public static int decodeChunkX(long key) {
        return signExtend16((int) ((key >>> 16) & 0xFFFF));
    }

    public static int decodeChunkZ(long key) {
        return signExtend16((int) (key & 0xFFFF));
    }

    public static int decodeBlockX(long key) {
        return (int) ((key >>> 14) & 0xF);
    }

    public static int decodeBlockY(long key) {
        return (int) ((key >>> 4) & 0x3FF) - 128;
    }

    public static int decodeBlockZ(long key) {
        return (int) (key & 0xF);
    }

    /** 16 位无符号值按有符号还原（>= 0x8000 视为负） */
    private static int signExtend16(int v) {
        return (v & 0x8000) != 0 ? v - 0x10000 : v;
    }
}
