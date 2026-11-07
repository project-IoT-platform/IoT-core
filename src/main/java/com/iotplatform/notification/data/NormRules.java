package com.iotplatform.notification.data;

import com.iotplatform.notification.model.NormOperator;

import java.util.UUID;

/** Изменение нормы устройства в таблицах rule и device_to_rule (команда /norm). */
public interface NormRules {
    enum Result { CREATED, UPDATED }

    /**
     * Ищет правило устройства с этим типом телеметрии и оператором.
     * Есть: меняет его value. Нет: создаёт правило и связь с устройством.
     */
    Result setNorm(UUID deviceId, short telemetryType, NormOperator operator, double value);
}