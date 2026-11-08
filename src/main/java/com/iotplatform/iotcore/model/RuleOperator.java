package com.iotplatform.iotcore.model;

/**
 * Оператор правила, как в типе rule_operator в БД. Правило выполняется, если «значение ОПЕРАТОР граница».
 * getSymbol() использует RuleEngine в тексте ошибки: «должно быть ≤ 30.0».
 */
public enum RuleOperator {
    GE("≥"), LE("≤"), GT(">"), LT("<"), EQ("="), NE("≠");

    private final String symbol;

    RuleOperator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    /** true, если значение удовлетворяет правилу. */
    public boolean test(double value, double bound) {
        return switch (this) {
            case GE -> value >= bound;
            case LE -> value <= bound;
            case GT -> value > bound;
            case LT -> value < bound;
            case EQ -> Double.compare(value, bound) == 0;
            case NE -> Double.compare(value, bound) != 0;
        };
    }
}