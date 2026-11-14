package com.iotplatform.notification;

import com.iotplatform.notification.api.ApiServer;
import com.iotplatform.notification.channel.Channel;
import com.iotplatform.notification.channel.Dispatcher;
import com.iotplatform.notification.channel.EmailChannel;
import com.iotplatform.notification.channel.TelegramChannel;
import com.iotplatform.notification.config.Config;
import com.iotplatform.notification.data.DeviceLimits;
import com.iotplatform.notification.data.PostgresChatLinks;
import com.iotplatform.notification.data.PostgresNormRules;
import com.iotplatform.notification.data.PostgresPlatformData;
import com.iotplatform.notification.data.PostgresRuleStore;
import com.iotplatform.notification.data.PostgresSettingsStore;
import com.iotplatform.notification.data.StubDeviceLimits;
import com.iotplatform.notification.db.Db;
import com.iotplatform.notification.email.EmailRenderer;
import com.iotplatform.notification.email.SmtpEmailSender;
import com.iotplatform.notification.service.NotificationService;
import com.iotplatform.notification.telegram.TelegramBot;
import com.iotplatform.notification.telegram.TelegramClient;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Точка входа сервиса уведомлений: читает настройки, собирает все части и запускает бота и HTTP-приёмник. */
public class NotificationServiceMain {
    public static void main(String[] args) throws Exception {
        Config config = Config.fromEnvironment();
        if (config.dbUrl == null) {
            System.err.println("Не задан DB_URL (а также DB_USER, DB_PASSWORD): адрес той же БД, что у IoT Core.");
            System.exit(1);
        }

        // данные: общая БД с IoT Core (сначала должен хотя бы раз запуститься IoT Core, он создаёт таблицы)
        Db db = new Db(config.dbUrl, config.dbUser, config.dbPassword);
        PostgresPlatformData platform = new PostgresPlatformData(db);
        PostgresChatLinks links = new PostgresChatLinks(db);
        PostgresSettingsStore settings = new PostgresSettingsStore(db, config.defaultNorm, config.defaultDeviation);
        PostgresRuleStore rules = new PostgresRuleStore(db);   // таблица rute: нужна только старой команде /battery
        PostgresNormRules normRules = new PostgresNormRules(db);   // /norm: таблицы rule и device_to_rule
        DeviceLimits limits = new StubDeviceLimits();   // ЗАГЛУШКА: заменить на класс тиммейта

        // каналы доставки
        TelegramClient telegram = new TelegramClient(
                config.telegramToken, config.telegramApiUrl, config.telegramProxy);
        SmtpEmailSender sender = new SmtpEmailSender(config);
        if (!telegram.isConfigured()) System.out.println("Telegram выключен: не задан TELEGRAM_BOT_TOKEN");
        if (config.smtpHost == null) {
            System.out.println("Email выключен: не задан SMTP_HOST");
        } else if (!sender.isConfigured()) {
            System.out.println("Email выключен из-за ошибок настройки: " + String.join("; ", config.smtpProblems()));
        }

        ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "notification-sender");
            t.setDaemon(true);
            return t;
        });
        Dispatcher dispatcher = new Dispatcher(List.<Channel>of(
                new TelegramChannel(telegram, links),
                new EmailChannel(sender, new EmailRenderer(config), platform)), pool);

        NotificationService service = new NotificationService(
                platform, settings, rules, limits, normRules, dispatcher, config.defaultBatteryLimit);
        TelegramBot bot = new TelegramBot(telegram, service, platform, links, config.zone);
        bot.start();

        ApiServer api = new ApiServer(config.httpPort, service);
        api.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            bot.stop();
            api.stop();
            pool.shutdown();
        }));
        System.out.println("Сервис уведомлений запущен на порту " + config.httpPort);
    }
}