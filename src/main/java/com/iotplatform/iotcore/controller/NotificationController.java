package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.service.NotificationService;
import com.iotplatform.iotcore.service.NotificationService.Settings;

import java.util.Map;
import java.util.UUID;

/**
 * Контроллер уведомлений: пробрасывает запросы к сервису уведомлений (статус устройства, общие настройки нормы).
 */
public class NotificationController {
    private final NotificationService service;

    /**
     * Создаёт контроллер.
     *
     * @param service клиент сервиса уведомлений
     */
    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /**
     * Просит сервис уведомлений отправить владельцу статус устройства.
     *
     * @param deviceId идентификатор устройства
     * @return карта с ключами {@code deviceId} и {@code state} (текущее состояние устройства)
     * @throws com.iotplatform.iotcore.exception.DeviceNotFoundException если сервис уведомлений не нашёл устройство
     * @throws UnsupportedOperationException если сервис уведомлений недоступен или ответил некорректно
     */
    public Map<String, Object> sendDeviceStatus(UUID deviceId) {
        return Map.of("deviceId", deviceId, "state", service.sendDeviceStatus(deviceId));
    }

    /**
     * Возвращает общие значения нормы и отклонения.
     *
     * @return текущие настройки из сервиса уведомлений
     * @throws UnsupportedOperationException если сервис уведомлений недоступен или ответил некорректно
     */
    public Settings getSettings() {
        return service.getDefaults();
    }

    /**
     * Меняет общие значения нормы и отклонения.
     *
     * @param settings новые значения
     * @return настройки после изменения
     * @throws com.iotplatform.iotcore.exception.ValidationException если сервис уведомлений отклонил значения
     * @throws UnsupportedOperationException если сервис уведомлений недоступен или ответил некорректно
     */
    public Settings updateSettings(Settings settings) {
        return service.updateDefaults(settings);
    }
}