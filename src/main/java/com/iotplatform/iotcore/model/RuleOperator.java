package com.iotplatform.iotcore.model;

public enum RuleOperator {
    GE(">="), LE("<="), GT(">"), LT("<"), EQ("=="), NE("!=");

    private final String symbol;

    RuleOperator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    // true, если значение actual удовлетворяет условию "actual <оператор> limit"
    public boolean test(double actual, double limit) {
        return switch (this) {
            case GE -> actual >= limit;
            case LE -> actual <= limit;
            case GT -> actual > limit;
            case LT -> actual < limit;
            case EQ -> actual == limit;
            case NE -> actual != limit;
        };
    }
}