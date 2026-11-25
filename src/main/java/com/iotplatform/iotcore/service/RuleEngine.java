package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.repository.RuleRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Проверка показаний по правилам, привязанным к устройству. Правила бывают строгие (показание отклоняется)
 * и нестрогие (данные сохраняются, нарушение только фиксируется).
 */
public class RuleEngine {
    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final RuleRepository rules;

    /**
     * Создаёт движок правил.
     *
     * @param rules хранилище правил
     */
    public RuleEngine(RuleRepository rules) {
        this.rules = rules;
    }

    /**
     * Проверяет показание по всем правилам устройства с тем же типом телеметрии.
     *
     * @param t проверяемое показание
     * @return нарушения нестрогих правил в виде «{@code rule_<id>}: сообщение»; пустая карта, если их нет
     * @throws ValidationException если нарушено хотя бы одно строгое правило (в ошибках все нарушенные строгие правила)
     */
    public Map<String, String> check(TelemetryDto t) {
        log.debug("Checking rules: device {}, type {}, value {}",
                t.deviceId(), t.telemetryType(), t.value());
        Map<String, String> strictErrors = new LinkedHashMap<>();
        Map<String, String> outOfRange = new LinkedHashMap<>();

        for (RuleEntity rule : rules.findForEvaluation(t.deviceId())) {
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