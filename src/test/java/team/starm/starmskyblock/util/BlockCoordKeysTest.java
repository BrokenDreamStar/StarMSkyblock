package team.starm.starmskyblock.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockCoordKeysTest {

    @Test
    void chunkKeyRoundTripPositive() {
        long key = BlockCoordKeys.encodeChunkKey(5, 100, 200);
        assertEquals(5, BlockCoordKeys.decodeWorldIndex(key));
        assertEquals(100, BlockCoordKeys.decodeChunkX(key));
        assertEquals(200, BlockCoordKeys.decodeChunkZ(key));
    }

    @Test
    void chunkKeyRoundTripNegative() {
        long key = BlockCoordKeys.encodeChunkKey(3, -1, -32767);
        assertEquals(3, BlockCoordKeys.decodeWorldIndex(key));
        assertEquals(-1, BlockCoordKeys.decodeChunkX(key));
        assertEquals(-32767, BlockCoordKeys.decodeChunkZ(key));
    }

    @Test
    void chunkKeyRoundTripBounds() {
        int[][] cases = {
                {0, 0, 0},
                {65535, 32767, 32767},
                {65535, -32767, -32767},
                {1, -32768, 32767}
        };
        for (int[] c : cases) {
            long key = BlockCoordKeys.encodeChunkKey(c[0], c[1], c[2]);
            assertEquals(c[0], BlockCoordKeys.decodeWorldIndex(key), "worldIndex");
            assertEquals(c[1], BlockCoordKeys.decodeChunkX(key), "chunkX");
            assertEquals(c[2], BlockCoordKeys.decodeChunkZ(key), "chunkZ");
        }
    }

    @Test
    void chunkKeyWorldIndexOverflowThrows() {
        assertThrows(IllegalStateException.class, () -> BlockCoordKeys.encodeChunkKey(65536, 0, 0));
        assertThrows(IllegalStateException.class, () -> BlockCoordKeys.encodeChunkKey(-1, 0, 0));
    }

    @Test
    void chunkKeysUniquePerInput() {
        Set<Long> keys = new HashSet<>();
        for (int w = 0; w < 3; w++) {
            for (int cx = -5; cx <= 5; cx++) {
                for (int cz = -5; cz <= 5; cz++) {
                    assertTrue(keys.add(BlockCoordKeys.encodeChunkKey(w, cx, cz)),
                            "collision at " + w + "," + cx + "," + cz);
                }
            }
        }
    }

    @Test
    void blockKeyRoundTrip() {
        int[][] cases = {
                {0, -64, 0}, {15, 319, 15}, {8, 0, 8}, {3, 100, 12}
        };
        for (int[] c : cases) {
            long key = BlockCoordKeys.encodeBlockKey(c[0], c[1], c[2]);
            assertEquals(c[0], BlockCoordKeys.decodeBlockX(key), "x");
            assertEquals(c[1], BlockCoordKeys.decodeBlockY(key), "y");
            assertEquals(c[2], BlockCoordKeys.decodeBlockZ(key), "z");
        }
    }

    @Test
    void blockKeyMasksOutOfRangeXZ() {
        // x/z 超出 0..15 被掩码截断（锁定现状）
        long key = BlockCoordKeys.encodeBlockKey(16, 0, 17);
        assertEquals(0, BlockCoordKeys.decodeBlockX(key)); // 16 & 0xF = 0
        assertEquals(1, BlockCoordKeys.decodeBlockZ(key)); // 17 & 0xF = 1
    }

    @Test
    void blockKeyRoundTripsFullYRange() {
        for (int y = -128; y <= 895; y += 73) {
            long key = BlockCoordKeys.encodeBlockKey(5, y, 5);
            assertEquals(y, BlockCoordKeys.decodeBlockY(key), "y=" + y);
        }
    }
}
