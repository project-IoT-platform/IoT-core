package com.iotplatform.iotcore;

import com.iotplatform.iotcore.controller.DeviceController;
import com.iotplatform.iotcore.controller.NotificationController;
import com.iotplatform.iotcore.controller.TelemetryController;
import com.iotplatform.iotcore.controller.UserController;
import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.http.HttpApi;
import com.iotplatform.iotcore.notification.IotCorePlatformData;
import com.iotplatform.iotcore.notification.NotificationServiceAdapter;
import com.iotplatform.iotcore.notification.TelegramLinksAdapter;
import com.iotplatform.iotcore.repository.DeviceRepository;
import com.iotplatform.iotcore.repository.PostgresTelegramLinkRepository;
import com.iotplatform.iotcore.repository.OffsetTelemetryRepository;
import com.iotplatform.iotcore.repository.PostgresUserRepository;
import com.iotplatform.iotcore.repository.TelegramLinkRepository;
import com.iotplatform.iotcore.repository.TelemetryRepository;
import com.iotplatform.iotcore.repository.UpsertDeviceRepository;
import com.iotplatform.iotcore.repository.UserRepository;
import com.iotplatform.iotcore.service.DeviceService;
import com.iotplatform.iotcore.service.NotificationService;
import com.iotplatform.iotcore.service.TelemetryService;
import com.iotplatform.iotcore.service.UserService;
import com.iotplatform.notification.NotificationModule;
import com.iotplatform.notification.config.NotificationConfig;

public class Main {
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        DatabaseUtils databaseUtils = DatabaseUtils.fromProperties("/db.properties");
        databaseUtils.initSchema();

        // ИЗМЕНЕНО: вместо PostgresDeviceRepository используется UpsertDeviceRepository
        // (исправленное сохранение и чтение устройства, класс тиммейтов не тронут)
        DeviceRepository deviceRepository = new UpsertDeviceRepository(databaseUtils);
        UserRepository userRepository = new PostgresUserRepository(databaseUtils);
        TelemetryRepository telemetryRepository = new OffsetTelemetryRepository(databaseUtils);
        TelegramLinkRepository linkRepository = new PostgresTelegramLinkRepository(databaseUtils);

        // NotificationService: Telegram-бот + email.
        // Секреты берутся из переменных окружения (TELEGRAM_BOT_TOKEN, SMTP_*, NORM, DEVIATION)
        // или из файла по пути NOTIFICATION_CONFIG_FILE. В коде секретов нет.
        // Без токена бот не запустится, без SMTP_HOST email выключен, остальное работает.
        NotificationConfig notificationConfig = NotificationConfig.fromEnvironment();
        NotificationModule notifications = NotificationModule.create(notificationConfig,
                new IotCorePlatformData(deviceRepository, userRepository, telemetryRepository),
                new TelegramLinksAdapter(linkRepository));
        notifications.start();
        Runtime.getRuntime().addShutdownHook(new Thread(notifications::close));
        NotificationService notificationService = new NotificationServiceAdapter(notifications.service());

        DeviceService deviceService = new DeviceService(deviceRepository, userRepository);
        UserService userService = new UserService(userRepository);
        TelemetryService telemetryService = new TelemetryService(deviceRepository, telemetryRepository);

        // УДАЛЕНО: отладочные блоки «РУЧНАЯ ПРОВЕРКА» и «ТЕСТ last_trans».
        // Они писали в БД при каждом старте и обходили ошибку чтения, которая теперь исправлена.

        HttpApi api = new HttpApi(port,
                new DeviceController(deviceService, notificationService),
                new UserController(userService),
                new TelemetryController(telemetryService, notificationService),
                new NotificationController(notificationService));
        api.start();
    }
}