package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.entity.TelemetryEntity;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.repository.DeviceRepository;
import com.iotplatform.iotcore.repository.TelemetryRepository;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Приём телеметрии: валидация, проверка правил, сохранение показания и обновление времени последних данных устройства.
 */
public class TelemetryService {
    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final DeviceRepository devices;
    private final TelemetryRepository telemetry;
    private final RuleEngine ruleEngine;

    /**
     * Создаёт сервис.
     *
     * @param devices хранилище устройств
     * @param telemetry хранилище телеметрии
     * @param ruleEngine движок правил
     */
    public TelemetryService(DeviceRepository devices, TelemetryRepository telemetry, RuleEngine ruleEngine) {
        this.devices = devices;
        this.telemetry = telemetry;
        this.ruleEngine = ruleEngine;
    }

    /**
     * Обрабатывает показание. Если нарушено строгое правило, показание не сохраняется. Иначе оно сохраняется
     * и у устройства обновляется время последних данных.
     *
     * @param dto показание
     * @return нарушения нестрогих правил (пустая карта, если их нет)
     * @throws com.iotplatform.iotcore.exception.ValidationException если показание некорректно или нарушает строгое правило
     * @throws DeviceNotFoundException если устройства нет
     */
    public Map<String, String> process(TelemetryDto dto) {
        dto.validate();
        if (!devices.existsById(dto.deviceId())) {
            throw new DeviceNotFoundException(dto.deviceId());
        }

        Map<String, String> outOfRange = ruleEngine.check(dto);

        telemetry.save(new TelemetryEntity(UUID.randomUUID(), dto.deviceId(),
                dto.time(), dto.telemetryType(), dto.value()));

        devices.updateLastDataTime(dto.deviceId(), dto.time());

        log.debug("Telemetry saved: device {}, type {}, value {}",
                dto.deviceId(), dto.telemetryType(), dto.value());
        return outOfRange;
    }
    static String describe(TelemetryDto dto) {
        return "устройство " + dto.deviceId() + ", тип " + dto.telemetryType() + ", значение " + dto.value();
    }
}