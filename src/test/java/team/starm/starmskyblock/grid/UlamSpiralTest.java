package team.starm.starmskyblock.grid;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UlamSpiralTest {

    @Test
    void originIsZeroZero() {
        int[] o = UlamSpiral.spiralOffset(0);
        assertEquals(0, o[0]);
        assertEquals(0, o[1]);
    }

    @Test
    void firstNineCellsMatchHandCalculation() {
        // index 0..8: (0,0)(1,0)(1,1)(0,1)(-1,1)(-1,0)(-1,-1)(0,-1)(1,-1)
        int[][] expected = {
                {0, 0}, {1, 0}, {1, 1}, {0, 1}, {-1, 1},
                {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
        };
        for (int i = 0; i < expected.length; i++) {
            int[] o = UlamSpiral.spiralOffset(i);
            assertEquals(expected[i][0], o[0], "index " + i + " x");
            assertEquals(expected[i][1], o[1], "index " + i + " z");
        }
    }

    @Test
    void matchesReferenceSpiralForFirst1000() {
        int[][] reference = referenceSpiral(1000);
        for (int i = 0; i < 1000; i++) {
            int[] o = UlamSpiral.spiralOffset(i);
            assertEquals(reference[i][0], o[0], "index " + i + " x");
            assertEquals(reference[i][1], o[1], "index " + i + " z");
        }
    }

    @Test
    void first501CellsAreUnique() {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i <= 500; i++) {
            int[] o = UlamSpiral.spiralOffset(i);
            assertTrue(seen.add(((long) o[0] << 32) | (o[1] & 0xFFFFFFFFL)),
                    "duplicate at index " + i);
        }
        assertEquals(501, seen.size());
    }

    @Test
    void consecutiveCellsAreManhattanAdjacent() {
        int[] prev = UlamSpiral.spiralOffset(0);
        for (int i = 1; i < 2000; i++) {
            int[] cur = UlamSpiral.spiralOffset(i);
            int dist = Math.abs(cur[0] - prev[0]) + Math.abs(cur[1] - prev[1]);
            assertEquals(1, dist, "index " + i + " not adjacent to " + (i - 1));
            prev = cur;
        }
    }

    /** 朴素 O(n) 螺旋生成器，作为参考实现：右、上、左、下，每两段长度 +1。 */
    private static int[][] referenceSpiral(int count) {
        int[][] cells = new int[count][];
        int x = 0, z = 0;
        int dx = 1, dz = 0;
        int segment = 1;
        int segCount = 0;
        int segTurns = 0;
        for (int i = 0; i < count; i++) {
            cells[i] = new int[]{x, z};
            x += dx;
            z += dz;
            if (++segCount == segment) {
                segCount = 0;
                int ndx = -dz, ndz = dx;
                dx = ndx;
                dz = ndz;
                if (++segTurns % 2 == 0) segment++;
            }
        }
        return cells;
    }
}
