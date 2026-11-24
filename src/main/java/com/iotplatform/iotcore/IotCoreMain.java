package com.iotplatform.iotcore;

import com.iotplatform.iotcore.controller.*;
import com.iotplatform.iotcore.check.StubBatteryChecker;
import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.http.HttpApi;
import com.iotplatform.iotcore.repository.*;
import com.iotplatform.iotcore.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Точка входа IoT Core.
 * Собирает слои приложения (БД, репозитории, сервисы, контроллеры) и запускает HTTP-сервер.
 */
public class IotCoreMain {
    private static final Logger log = LoggerFactory.getLogger(IotCoreMain.class);

    /**
     * Запускает IoT Core.
     * Порт берётся из переменной окружения {@code PORT} (по умолчанию 8082), адрес сервиса уведомлений из
     * {@code NOTIFICATION_URL} (по умолчанию {@code http://localhost:8081}). Параметры БД читаются из {@code /db.properties},
     * затем выполняется {@code schema.sql}, создаются репозитории, сервисы и контроллеры, поднимается {@link com.iotplatform.iotcore.http.HttpApi}.
     *
     * @param args аргументы командной строки, не используются
     * @throws NumberFormatException если {@code PORT} задан не числом
     * @throws RuntimeException если не удалось прочитать {@code db.properties} или выполнить {@code schema.sql}
     * @throws IllegalStateException если HTTP-сервер не смог занять порт
     */
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8082"));
        // Адрес сервиса уведомлений. Telegram-токен, SMTP и нормы теперь живут в нём, а не здесь.
        String notificationUrl = System.getenv().getOrDefault("NOTIFICATION_URL", "http://localhost:8081");

        DatabaseUtils databaseUtils = DatabaseUtils.fromProperties("/db.properties");
        databaseUtils.initSchema();
        log.info("Database schema initialized");
        DeviceRepository deviceRepository = new PostgresDeviceRepository(databaseUtils);
        UserRepository userRepository = new PostgresUserRepository(databaseUtils);
        TelemetryRepository telemetryRepository = new PostgresTelemetryRepository(databaseUtils);
        RuleRepository ruleRepository = new PostgresRuleRepository(databaseUtils);
        RuleEngine ruleEngine = new RuleEngine(ruleRepository);

        NotificationService notificationService = new NotificationService(notificationUrl);

        DeviceService deviceService = new DeviceService(deviceRepository, userRepository);
        UserService userService = new UserService(userRepository);
        TelemetryService telemetryService = new TelemetryService(deviceRepository, telemetryRepository, ruleEngine);
        RuleService ruleService = new RuleService(ruleRepository, deviceRepository);
        // ЗАГЛУШКИ: когда тиммейт допишет проверки, подставить сюда его классы вместо Stub...
        AlertService alertService = new AlertService(new StubBatteryChecker(),
                deviceRepository, notificationService);

        HttpApi api = new HttpApi(port,
                new DeviceController(deviceService, notificationService, alertService),
                new UserController(userService, notificationService),
                new TelemetryController(telemetryService, alertService),
                new NotificationController(notificationService),
                new RuleController(ruleService));
        api.start();
    }
}