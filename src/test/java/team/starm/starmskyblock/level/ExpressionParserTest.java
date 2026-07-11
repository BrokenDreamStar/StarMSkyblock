package team.starm.starmskyblock.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionParserTest {

    @Test
    void basicAdditionSubtraction() {
        assertEquals(5, ExpressionParser.evaluate("2+3"), 1e-9);
        assertEquals(6, ExpressionParser.evaluate("10-4"), 1e-9);
    }

    @Test
    void operatorPrecedence() {
        assertEquals(14, ExpressionParser.evaluate("2+3*4"), 1e-9);
        assertEquals(10, ExpressionParser.evaluate("2*3+4"), 1e-9);
    }

    @Test
    void leftAssociativeSubtractionAndDivision() {
        assertEquals(5, ExpressionParser.evaluate("10-3-2"), 1e-9);
        assertEquals(2, ExpressionParser.evaluate("8/2/2"), 1e-9);
    }

    @Test
    void parentheses() {
        assertEquals(20, ExpressionParser.evaluate("(2+3)*4"), 1e-9);
        assertEquals(21, ExpressionParser.evaluate("((1+2)*(3+4))"), 1e-9);
    }

    @Test
    void division() {
        assertEquals(2.5, ExpressionParser.evaluate("10/4"), 1e-9);
    }

    @Test
    void decimals() {
        assertEquals(4, ExpressionParser.evaluate("1.5+2.5"), 1e-9);
        assertEquals(0.3, ExpressionParser.evaluate("0.1+0.2"), 1e-9);
    }

    @Test
    void whitespaceStripped() {
        assertEquals(5, ExpressionParser.evaluate("  2 + 3  "), 1e-9);
        assertEquals(14, ExpressionParser.evaluate(" 2 + 3 * 4 "), 1e-9);
    }

    @Test
    void emptyStringReturnsZero() {
        assertEquals(0, ExpressionParser.evaluate(""), 1e-9);
        assertEquals(0, ExpressionParser.evaluate("   "), 1e-9);
    }

    @Test
    void trailingOperatorLeavesCurrentTotal() {
        // parsePrimary 越界返回 0，故 "5+" -> 5+0 = 5（锁定现状）
        assertEquals(5, ExpressionParser.evaluate("5+"), 1e-9);
    }

    @Test
    void divisionByZeroIsInfinity() {
        double result = ExpressionParser.evaluate("5/0");
        assertTrue(Double.isInfinite(result) && result > 0, "expected +Infinity, got " + result);
    }
}
