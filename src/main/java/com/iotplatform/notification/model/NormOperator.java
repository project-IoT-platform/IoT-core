package com.iotplatform.notification.model;

import java.util.Optional;

/** Оператор в команде /norm. В БД пишется как значение типа rule_operator (GE или LE). */
public enum NormOperator {
    GE(">="), LE("<=");

    private final String symbol;

    NormOperator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    /** «>=» или «≥» -> GE, «<=» или «≤» -> LE, остальное -> пусто. */
    public static Optional<NormOperator> fromSymbol(String s) {
        if (s == null) return Optional.empty();
        return switch (s.trim()) {
            case ">=", "≥", "=>" -> Optional.of(GE);
            case "<=", "≤", "=<" -> Optional.of(LE);
            default -> Optional.empty();
        };
    }
}