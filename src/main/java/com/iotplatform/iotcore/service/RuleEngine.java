package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.repository.RuleRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Проверка правил (функция тиммейта, перенесена со скриншота ветки rule-engine).
 * Строгое нарушение: ValidationException (HTTP 400). Нестрогие нарушения возвращаются словарём «rule_<id> -> текст».
 * Если у тиммейта файл отличается (например, логирование через SLF4J), берите его версию: интерфейс тот же.
 */
public class RuleEngine {
    private static final System.Logger log = System.getLogger(RuleEngine.class.getName());

    private final RuleRepository rules;

    public RuleEngine(RuleRepository rules) {
        this.rules = rules;
    }

    public Map<String, String> check(TelemetryDto t) {
        log.log(System.Logger.Level.DEBUG, "Checking rules: device " + t.deviceId() + ", type " + t.telemetryType()
                + ", value " + t.value());
        Map<String, String> strictErrors = new LinkedHashMap<>();
        Map<String, String> outOfRange = new LinkedHashMap<>();

        for (RuleEntity rule : rules.findByDeviceId(t.deviceId())) {
            if (!rule.telemetryType().equals(t.telemetryType())) continue;
            if (rule.operator().test(t.value(), rule.value())) continue;   // правило выполнено

            String message = "значение " + t.value() + " нарушает правило: должно быть "
                    + rule.operator().getSymbol() + " " + rule.value();

            if (rule.strict()) {
                strictErrors.put("rule_" + rule.id(), message);
            } else {
                outOfRange.put("rule_" + rule.id(), message);
            }
        }

        if (!strictErrors.isEmpty()) throw new ValidationException(strictErrors);
        return outOfRange;
    }
}