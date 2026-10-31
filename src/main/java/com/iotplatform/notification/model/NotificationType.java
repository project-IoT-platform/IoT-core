package com.iotplatform.notification.model;

/**
 * Тип уведомления: заголовок, бейдж и цвет акцента в письме, нужно ли слать письмо.
 * Telegram получает ВСЕ типы. Email: только DEVICE_REGISTERED и TELEGRAM_ALERT (см. последний аргумент).
 */
public enum NotificationType {
    DEVICE_REGISTERED("Устройство зарегистрировано", "Регистрация", "#1e88e5", true),
    // ИЗМЕНЕНО: было true. Низкий заряд теперь только в Telegram, письмо не отправляется
    LOW_BATTERY("Низкий заряд батареи", "Батарея", "#f9a825", false),
    TELEMETRY_ALERT("Отклонение показателей", "Предупреждение", "#e53935", true),
    DEVICE_STATUS("Статус устройства", "Статус", "#43a047", false);

    private final String title;
    private final String badge;
    private final String accentColor;
    private final boolean sendByEmail;

    NotificationType(String title, String badge, String accentColor, boolean sendByEmail) {
        this.title = title;
        this.badge = badge;
        this.accentColor = accentColor;
        this.sendByEmail = sendByEmail;
    }

    public String title() { return title; }
    public String badge() { return badge; }
    public String accentColor() { return accentColor; }
    public boolean sendByEmail() { return sendByEmail; }
}