package com.iotplatform.iotcore.entity;

import com.iotplatform.iotcore.model.RuleOperator;

import java.util.UUID;

public record RuleEntity(
        UUID id,
        Short telemetryType,
        RuleOperator operator,
        Double value) {
}