package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.entity.TelemetryEntity;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.repository.DeviceRepository;
import com.iotplatform.iotcore.repository.TelemetryRepository;

import java.util.Map;
import java.util.UUID;

/**
 * Приём показаний. Порядок: проверка полей -> устройство существует -> RuleEngine.check -> сохранение.
 * Нарушено строгое правило: RuleEngine бросает ValidationException (HTTP 400), в БД не пишем.
 * Нестрогие нарушения: сохраняем и возвращаем их контроллеру (он пишет в лог и отправляет на проверку нормы).
 */
public class TelemetryService {
    private static final System.Logger LOG = System.getLogger(TelemetryService.class.getName());

    private final DeviceRepository devices;
    private final TelemetryRepository telemetry;
    private final RuleEngine ruleEngine;

    public TelemetryService(DeviceRepository devices, TelemetryRepository telemetry, RuleEngine ruleEngine) {
        this.devices = devices;
        this.telemetry = telemetry;
        this.ruleEngine = ruleEngine;
    }

    /** Возвращает нестрогие нарушения (пусто, если всё в порядке). */
    public Map<String, String> process(TelemetryDto dto) {
        dto.validate();
        if (!devices.existsById(dto.deviceId())) {
            throw new DeviceNotFoundException(dto.deviceId());
        }

        Map<String, String> outOfRange;
        try {
            outOfRange = ruleEngine.check(dto);
        } catch (ValidationException e) {
            LOG.log(System.Logger.Level.WARNING, "ОТКЛОНЕНО (строгое нарушение): " + describe(dto) + " -> " + e.getErrors());
            throw e;
        }

        telemetry.save(new TelemetryEntity(UUID.randomUUID(), dto.deviceId(),
                dto.time(), dto.telemetryType(), dto.value()));
        // обновляем только last_trans, а не сохраняем устройство целиком
        devices.updateLastDataTime(dto.deviceId(), dto.time());
        LOG.log(System.Logger.Level.INFO, "СОХРАНЕНО: " + describe(dto));
        return outOfRange;
    }

    static String describe(TelemetryDto dto) {
        return "устройство " + dto.deviceId() + ", тип " + dto.telemetryType() + ", значение " + dto.value();
    }
}