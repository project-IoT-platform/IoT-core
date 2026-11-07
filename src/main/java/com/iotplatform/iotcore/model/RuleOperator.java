package com.iotplatform.iotcore.model;

public enum RuleOperator {
    GE(">="), LE("<="), GT(">"), LT("<"), EQ("=="), NE("!=");

    private final String symbol;

    private static final double EPSILON = 1e-9;

    RuleOperator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    private static boolean equalWithTolerance(double a, double b) {
        return a == b || Math.abs(a - b) <= EPSILON;
    }

    public boolean test(double actual, double limit) {
        return switch (this) {
            case GE -> actual > limit || equalWithTolerance(actual, limit);
            case LE -> actual < limit || equalWithTolerance(actual, limit);
            case GT -> actual > limit && !equalWithTolerance(actual, limit);
            case LT -> actual < limit && !equalWithTolerance(actual, limit);
            case EQ -> equalWithTolerance(actual, limit);
            case NE -> !equalWithTolerance(actual, limit);
        };
    }
}