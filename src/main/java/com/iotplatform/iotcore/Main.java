package com.iotplatform.iotcore;

import com.iotplatform.iotcore.controller.*;
import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.http.HttpApi;
import com.iotplatform.iotcore.repository.*;
import com.iotplatform.iotcore.service.*;
import com.iotplatform.iotcore.telegram.TelegramBot;
import com.iotplatform.iotcore.telegram.TelegramClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

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
        log.info("Database schema initialized");
        DeviceRepository deviceRepository = new PostgresDeviceRepository(databaseUtils);
        UserRepository userRepository = new PostgresUserRepository(databaseUtils);
        TelemetryRepository telemetryRepository = new PostgresTelemetryRepository(databaseUtils);
        TelegramLinkRepository linkRepository = new PostgresTelegramLinkRepository(databaseUtils);
        RuleRepository ruleRepository = new PostgresRuleRepository(databaseUtils);
        RuleEngine ruleEngine = new RuleEngine(ruleRepository);

        TelegramClient telegram = new TelegramClient(botToken);
        NotificationService notificationService = new NotificationService(
                telegram, deviceRepository, linkRepository, telemetryRepository, norm, deviation);
        TelegramBot bot = new TelegramBot(telegram, notificationService, userRepository, linkRepository);
        bot.start();
        Runtime.getRuntime().addShutdownHook(new Thread(bot::stop));

        DeviceService deviceService = new DeviceService(deviceRepository, userRepository);
        UserService userService = new UserService(userRepository);
        TelemetryService telemetryService = new TelemetryService(deviceRepository, telemetryRepository, ruleEngine);
        RuleService ruleService = new RuleService(ruleRepository, deviceRepository);

        HttpApi api = new HttpApi(port,
                new DeviceController(deviceService, notificationService),
                new UserController(userService),
                new TelemetryController(telemetryService, notificationService),
                new NotificationController(notificationService),
                new RuleController(ruleService));
        api.start();
    }
}