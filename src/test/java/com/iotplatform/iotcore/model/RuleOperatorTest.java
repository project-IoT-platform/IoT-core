package com.iotplatform.iotcore.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleOperatorTest {

    // Предел 10: проверяем значение ниже (9.9), равное (10.0) и выше (10.1)
    private static void check(RuleOperator op, boolean below, boolean equal, boolean above) {
        expect(below, op.test(9.9, 10.0), op + ": значение ниже предела");
        expect(equal, op.test(10.0, 10.0), op + ": значение равно пределу");
        expect(above, op.test(10.1, 10.0), op + ": значение выше предела");
    }

    private static void expect(boolean expected, boolean actual, String message) {
        if (expected) assertTrue(actual, message);
        else assertFalse(actual, message);
    }

    @Test
    void geAcceptsEqualAndAbove() {
        check(RuleOperator.GE, false, true, true);
    }

    @Test
    void leAcceptsEqualAndBelow() {
        check(RuleOperator.LE, true, true, false);
    }

    @Test
    void gtRejectsEqual() {
        check(RuleOperator.GT, false, false, true);
    }

    @Test
    void ltRejectsEqual() {
        check(RuleOperator.LT, true, false, false);
    }

    @Test
    void eqAcceptsOnlyEqual() {
        check(RuleOperator.EQ, false, true, false);
    }

    @Test
    void neRejectsOnlyEqual() {
        check(RuleOperator.NE, true, false, true);
    }

    @Test
    void symbolsMatchOperators() {
        assertEquals(">=", RuleOperator.GE.getSymbol());
        assertEquals("<=", RuleOperator.LE.getSymbol());
        assertEquals(">", RuleOperator.GT.getSymbol());
        assertEquals("<", RuleOperator.LT.getSymbol());
        assertEquals("==", RuleOperator.EQ.getSymbol());
        assertEquals("!=", RuleOperator.NE.getSymbol());
    }

    @Test
    void worksWithNegativeNumbers() {
        assertTrue(RuleOperator.LE.test(-5, 0));
        assertFalse(RuleOperator.GE.test(-5, 0));
        assertTrue(RuleOperator.LT.test(-10, -5));
    }

    @Test
    void zeroEqualsNegativeZero() {
        assertTrue(RuleOperator.EQ.test(0.0, -0.0));
    }

    @Test
    void eqIsExactForDecimalParsedFromJson() {
        // 0.3 из JSON и 0.3 в правиле — это один и тот же double
        assertTrue(RuleOperator.EQ.test(0.3, 0.3));
        assertFalse(RuleOperator.NE.test(0.3, 0.3));
    }

    // 0.1 + 0.2 в double равно 0.30000000000000004, а не 0.3 — допуск на погрешность это сглаживает
    @Test
    void eqToleratesFloatingPointRoundingError() {
        assertTrue(RuleOperator.EQ.test(0.1 + 0.2, 0.3));
    }

    @Test
    void neToleratesFloatingPointRoundingError() {
        assertFalse(RuleOperator.NE.test(0.1 + 0.2, 0.3));
    }

    @Test
    void leAcceptsValueSlightlyAboveLimitBecauseOfRounding() {
        assertTrue(RuleOperator.LE.test(0.1 + 0.2, 0.3));   // 0.30000000000000004 <= 0.3
    }

    @Test
    void geAcceptsValueSlightlyBelowLimitBecauseOfRounding() {
        assertTrue(RuleOperator.GE.test(0.7 + 0.1, 0.8));   // 0.7999999999999999 >= 0.8
    }

    @Test
    void gtAndLtIgnoreRoundingNoise() {
        assertFalse(RuleOperator.GT.test(0.1 + 0.2, 0.3));
        assertFalse(RuleOperator.LT.test(0.7 + 0.1, 0.8));
    }

    @Test
    void realDifferenceIsStillADifference() {
        assertFalse(RuleOperator.EQ.test(1.0, 1.000001));
        assertTrue(RuleOperator.NE.test(1.0, 1.000001));
        assertTrue(RuleOperator.GT.test(1.000001, 1.0));
        assertTrue(RuleOperator.LT.test(1.0, 1.000001));
        assertFalse(RuleOperator.LE.test(1.000001, 1.0));
    }

    @Test
    void largeNumbersAreStillComparedExactly() {
        assertFalse(RuleOperator.EQ.test(1_700_000_000_000.0, 1_700_000_000_001.0));
        assertTrue(RuleOperator.LT.test(1_700_000_000_000.0, 1_700_000_000_001.0));
    }

    @Test
    void infinitiesBehaveAsBefore() {
        assertTrue(RuleOperator.EQ.test(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
        assertFalse(RuleOperator.EQ.test(1e308, Double.POSITIVE_INFINITY));
        assertTrue(RuleOperator.LT.test(1e308, Double.POSITIVE_INFINITY));
    }

    @Test
    void nanIsNeverEqualToAnything() {
        assertFalse(RuleOperator.EQ.test(Double.NaN, 1.0));
        assertTrue(RuleOperator.NE.test(Double.NaN, 1.0));
        assertFalse(RuleOperator.GE.test(Double.NaN, 1.0));
        assertFalse(RuleOperator.LE.test(Double.NaN, 1.0));
    }
}