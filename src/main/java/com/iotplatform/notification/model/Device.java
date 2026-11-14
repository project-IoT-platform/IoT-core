package com.iotplatform.notification.model;

import java.time.Instant;
import java.util.UUID;

/** Устройство, как его видит сервис уведомлений. userId может быть null (устройство без владельца). */
public record Device(UUID id, UUID userId, String name, String manufacturer, Integer batteryLevel,
                     boolean on, String geolocation, Instant lastDataTime) {}
