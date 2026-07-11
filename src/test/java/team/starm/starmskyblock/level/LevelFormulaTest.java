package team.starm.starmskyblock.level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelFormulaTest {

    @Test
    void belowBaseThresholdIsLevelZero() {
        assertEquals(0, LevelFormula.fromPowerCurve(0, 50, 1.2));
        assertEquals(0, LevelFormula.fromPowerCurve(49, 50, 1.2)); // round(50)=50, 49<50
    }

    @Test
    void linearCurveMatchesArithmeticSeries() {
        // cost(L)=100L, cumulative(L)=100*L*(L+1)/2
        assertEquals(0, LevelFormula.fromPowerCurve(50, 100, 1.0));  // 50 < round(100)=100
        assertEquals(2, LevelFormula.fromPowerCurve(550, 100, 1.0)); // cum(3)=600 > 550
        assertEquals(3, LevelFormula.fromPowerCurve(600, 100, 1.0)); // cum(3)=600, 600>600? no; cum(4)=1000>600 -> 3
        assertEquals(3, LevelFormula.fromPowerCurve(999, 100, 1.0)); // cum(4)=1000>999 -> 3
    }

    @Test
    void powerZeroConstantCost() {
        // cost(L)=round(base)=100, cumulative(L)=100*L
        assertEquals(2, LevelFormula.fromPowerCurve(250, 100, 0));
        assertEquals(0, LevelFormula.fromPowerCurve(50, 100, 0)); // 50 < round(100)
    }

    @Test
    void overflowGuardTerminatesOnNegativePower() {
        // power=-1: cost(L)=round(50/L) 趋 0；cost<=0 守卫在 L=101 终止，返回 100
        int level = LevelFormula.fromPowerCurve(Double.MAX_VALUE, 50, -1.0);
        assertEquals(100, level);
    }

    @Test
    void overflowGuardTerminatesOnLargeTotalExp() {
        // 极大经验 + 高 power 不死循环（cost 增长极快，几级即超 totalExp）
        int level = LevelFormula.fromPowerCurve(1e18, 1e10, 10.0);
        assertTrue(level >= 0 && level < 1000, "level=" + level);
    }

    @Test
    void diminishingReturnsZeroOverLimit() {
        assertEquals(0, LevelFormula.diminishingReturns(0, 10, 1.0, 0.0), 1e-9);
    }

    @Test
    void diminishingReturnsHandCalculated() {
        // overLimit=3, expValue=10, decay=1.0, minimum=0:
        // i=0: round(max(10/1,0))=10; i=1: round(10/2)=5; i=2: round(10/3)=3 -> 18
        assertEquals(18, LevelFormula.diminishingReturns(3, 10, 1.0, 0.0), 1e-9);
    }

    @Test
    void diminishingReturnsRespectsMinimum() {
        // expValue=1, decay=1.0, minimum=0.5: i>=1 时 max(1/(1+i),0.5)=0.5, round(0.5)=1
        // i=0: round(max(1,0.5))=1; i=1: round(0.5)=1; i=2: round(max(0.333,0.5))=round(0.5)=1 -> 3
        assertEquals(3, LevelFormula.diminishingReturns(3, 1, 1.0, 0.5), 1e-9);
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void diminishingReturnsLargeOverLimitCompletes() {
        // #11: 大 overLimit 的 O(n) 循环在合理时间内完成且结果有限
        double result = LevelFormula.diminishingReturns(1_000_000, 1, 0.001, 0.0);
        assertTrue(Double.isFinite(result) && result > 0);
    }
}
