package com.iotplatform.notification.data;

import com.iotplatform.notification.model.Settings;

/**
 * Общие норма и отклонение по умолчанию (GET/PUT /settings).
 * ИСПРАВЛЕНО: раньше файл был на Kotlin (SettingsStore.kt) в Java-проекте без Kotlin-плагина, проект не собирался.
 */
public interface SettingsStore {
    Settings defaults();

    void saveDefaults(Settings s);
}