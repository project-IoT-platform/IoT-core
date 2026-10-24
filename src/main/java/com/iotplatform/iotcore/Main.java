package com.iotplatform.iotcore;

import com.iotplatform.iotcore.controller.DeviceController;
import com.iotplatform.iotcore.controller.NotificationController;
import com.iotplatform.iotcore.controller.TelemetryController;
import com.iotplatform.iotcore.controller.UserController;
import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.http.HttpApi;
import com.iotplatform.iotcore.repository.DeviceRepository;
import com.iotplatform.iotcore.repository.PostgresDeviceRepository;
import com.iotplatform.iotcore.repository.PostgresTelegramLinkRepository;
import com.iotplatform.iotcore.repository.PostgresTelemetryRepository;
import com.iotplatform.iotcore.repository.PostgresUserRepository;
import com.iotplatform.iotcore.repository.TelegramLinkRepository;
import com.iotplatform.iotcore.repository.TelemetryRepository;
import com.iotplatform.iotcore.repository.UserRepository;
import com.iotplatform.iotcore.service.DeviceService;
import com.iotplatform.iotcore.service.NotificationService;
import com.iotplatform.iotcore.service.TelemetryService;
import com.iotplatform.iotcore.service.UserService;
import com.iotplatform.iotcore.telegram.TelegramBot;
import com.iotplatform.iotcore.telegram.TelegramClient;

public class Main {
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        // Токен бота берём ТОЛЬКО из переменной окружения TELEGRAM_BOT_TOKEN, в коде его хранить нельзя.
        // Если переменной нет, бот просто не запустится (остальное будет работать).
        String botToken = System.getenv("TELEGRAM_BOT_TOKEN");
        // стартовые значения нормы и отклонения (каждый пользователь потом меняет свои через бота)
        double norm      = Double.parseDouble(System.getenv().getOrDefault("NORM", "25"));
        double deviation = Double.parseDouble(System.getenv().getOrDefault("DEVIATION", "5"));

        DatabaseUtils databaseUtils = DatabaseUtils.fromProperties("/db.properties");
        databaseUtils.initSchema();
        DeviceRepository deviceRepository = new PostgresDeviceRepository(databaseUtils);
        UserRepository userRepository = new PostgresUserRepository(databaseUtils);
        TelemetryRepository telemetryRepository = new PostgresTelemetryRepository(databaseUtils);
        TelegramLinkRepository linkRepository = new PostgresTelegramLinkRepository(databaseUtils);

        TelegramClient telegram = new TelegramClient(botToken);
        NotificationService notificationService = new NotificationService(
                telegram, deviceRepository, linkRepository, telemetryRepository, norm, deviation);
        TelegramBot bot = new TelegramBot(telegram, notificationService, userRepository, linkRepository);
        bot.start();
        Runtime.getRuntime().addShutdownHook(new Thread(bot::stop));

        DeviceService deviceService = new DeviceService(deviceRepository, userRepository);
        UserService userService = new UserService(userRepository);
        TelemetryService telemetryService = new TelemetryService(deviceRepository, telemetryRepository);

        HttpApi api = new HttpApi(port,
                new DeviceController(deviceService, notificationService),
                new UserController(userService),
                new TelemetryController(telemetryService, notificationService),
                new NotificationController(notificationService));
        api.start();
    }
}