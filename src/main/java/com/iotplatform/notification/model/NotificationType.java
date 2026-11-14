package com.iotplatform.notification.model;

/**
 * Тип уведомления: заголовок, бейдж и цвет акцента в письме, нужно ли слать письмо.
 * В Telegram идут все типы. По почте: регистрация устройства, регистрация пользователя,
 * низкий заряд батареи и отклонение показателей.
 * Чтобы добавить новый тип, добавьте константу сюда и метод в Notifications.
 */
public enum NotificationType {
    DEVICE_REGISTERED("Устройство зарегистрировано", "Регистрация", "#1e88e5", true),
    USER_REGISTERED("Вы зарегистрированы на платформе", "Аккаунт", "#5e35b1", true),
    LOW_BATTERY("Низкий заряд батареи", "Батарея", "#f9a825", true),   // ИЗМЕНЕНО: теперь и по почте
    TELEMETRY_ALERT("Отклонение показателей", "Предупреждение", "#e53935", true),
    DEVICE_STATUS("Статус устройства", "Статус", "#43a047", false);

    private final String title;
    private final String badge;
    private final String accentColor;
    private final boolean byEmail;

    NotificationType(String title, String badge, String accentColor, boolean byEmail) {
        this.title = title;
        this.badge = badge;
        this.accentColor = accentColor;
        this.byEmail = byEmail;
    }

    public String title() { return title; }
    public String badge() { return badge; }
    public String accentColor() { return accentColor; }
    public boolean byEmail() { return byEmail; }
}