package com.iotplatform.iotcore.model;

/**
 * Оператор сравнения в правиле: {@code GE} ({@code >=}), {@code LE} ({@code <=}), {@code GT} ({@code >}),
 * {@code LT} ({@code <}), {@code EQ} ({@code ==}), {@code NE} ({@code !=}). Дробные числа сравниваются с допуском 1e-9.
 */
public enum RuleOperator {
    /** Больше или равно ({@code >=}). */
    GE(">="),
    /** Меньше или равно ({@code <=}). */
    LE("<="),
    /** Строго больше ({@code >}). */
    GT(">"),
    /** Строго меньше ({@code <}). */
    LT("<"),
    /** Равно ({@code ==}) с допуском 1e-9. */
    EQ("=="),
    /** Не равно ({@code !=}) с допуском 1e-9. */
    NE("!=");

    private final String symbol;

    private static final double EPSILON = 1e-9;

    RuleOperator(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Возвращает запись оператора, например {@code >=}.
     *
     * @return запись оператора, например {@code >=}
     */
    public String getSymbol() {
        return symbol;
    }

    private static boolean equalWithTolerance(double a, double b) {
        return a == b || Math.abs(a - b) <= EPSILON;
    }

    /**
     * Проверяет, выполняется ли условие {@code actual <оператор> limit}.
     *
     * @param actual фактическое значение показания
     * @param limit граничное значение из правила
     * @return {@code true}, если условие выполнено
     */
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