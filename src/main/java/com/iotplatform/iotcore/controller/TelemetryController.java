package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.repository.TelemetryRepository;
import com.iotplatform.iotcore.service.AlertService;
import com.iotplatform.iotcore.service.TelemetryService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TelemetryController {
    private static final System.Logger LOG = System.getLogger(TelemetryController.class.getName());

    private final TelemetryService service;
    private final AlertService alerts;

    public TelemetryController(TelemetryService service, AlertService alerts) {
        this.service = service;
        this.alerts = alerts;
    }

    public void receive(TelemetryDto dto) {
        // валидация, RuleEngine, сохранение; строгое нарушение -> 400, дальше не идём
        Map<String, String> outOfRange = service.process(dto);
        if (!outOfRange.isEmpty()) {
            LOG.log(System.Logger.Level.INFO, "СОХРАНЕНО (нестрогое нарушение): устройство " + dto.deviceId()
                    + ", тип " + dto.telemetryType() + ", значение " + dto.value() + " -> " + outOfRange);
        }
        alerts.onTelemetry(dto);   // проверка на норму -> при отклонении уведомление
    }

    // ---- НОВОЕ: чтение для интерфейса (RuleEngine и приём показаний не затронуты) ----

    public List<TelemetryRepository.Latest> latest(UUID deviceId) {
        return service.latest(deviceId);
    }

    public List<TelemetryRepository.Point> series(UUID deviceId, short type, Instant since, int limit) {
        return service.series(deviceId, type, since, limit);
    }

    public TelemetryRepository.Average average(UUID deviceId, short type, Instant since) {
        return service.average(deviceId, type, since);
    }
}