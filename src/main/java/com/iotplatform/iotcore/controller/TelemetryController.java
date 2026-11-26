package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.service.AlertService;
import com.iotplatform.iotcore.service.TelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iotplatform.iotcore.repository.TelemetryRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Контроллер приёма телеметрии: сохраняет показание, пишет нарушения правил в лог и запускает уведомления.
 */
public class TelemetryController {
    private static final Logger log = LoggerFactory.getLogger(TelemetryController.class);

    private final TelemetryService service;
    private final AlertService alerts;

    /**
     * Создаёт контроллер.
     *
     * @param service сервис обработки телеметрии
     * @param alerts сервис проверок на норму
     */
    public TelemetryController(TelemetryService service, AlertService alerts) {
        this.service = service;
        this.alerts = alerts;
    }

    public List<TelemetryDto> latest(UUID deviceId) {
        return service.latest(deviceId);
    }

    public List<TelemetryDto> recent(UUID deviceId, short type, int limit) {
        return service.recent(deviceId, type, limit);
    }

    public TelemetryRepository.Average average(UUID deviceId, short type, Instant since) {
        return service.average(deviceId, type, since);
    }

    /**
     * Принимает показание. Если нарушено строгое правило, показание отклоняется. Если нарушены только нестрогие,
     * данные сохраняются, нарушение пишется в лог и отправляется уведомление.
     *
     * @param dto показание устройства
     * @throws com.iotplatform.iotcore.exception.ValidationException если показание некорректно или нарушает строгое правило
     * @throws com.iotplatform.iotcore.exception.DeviceNotFoundException если устройства нет
     */
    public void receive(TelemetryDto dto) {
        Map<String, String> outOfRange;
        try {
            outOfRange = service.process(dto);
        } catch (ValidationException e) {
            log.warn("Telemetry rejected: device {}, type {}, value {} -> {}",
                    dto.deviceId(), dto.telemetryType(), dto.value(), e.getErrors());
            throw e;
        }

        if (!outOfRange.isEmpty()) {
            log.warn("Soft rule violation, data saved: device {}, type {}, value {} -> {}",
                    dto.deviceId(), dto.telemetryType(), dto.value(), outOfRange);
        }

        alerts.onTelemetry(dto, outOfRange);   // проверка на норму -> при отклонении уведомление
    }
}