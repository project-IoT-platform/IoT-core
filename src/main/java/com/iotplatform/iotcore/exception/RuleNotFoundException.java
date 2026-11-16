package com.iotplatform.iotcore.exception;

import java.util.UUID;

public class RuleNotFoundException extends RuntimeException {
    public RuleNotFoundException(UUID id) {
        super("Правило не найдено: " + id);
    }
}