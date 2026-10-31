package com.iotplatform.notification;

import com.iotplatform.notification.channel.EmailChannel;
import com.iotplatform.notification.channel.NotificationChannel;
import com.iotplatform.notification.channel.NotificationDispatcher;
import com.iotplatform.notification.channel.TelegramChannel;
import com.iotplatform.notification.config.EmailConfig;
import com.iotplatform.notification.config.NotificationConfig;
import com.iotplatform.notification.email.EmailRenderer;
import com.iotplatform.notification.email.EmailSender;
import com.iotplatform.notification.email.SmtpEmailSender;
import com.iotplatform.notification.port.PlatformData;
import com.iotplatform.notification.port.TelegramLinks;
import com.iotplatform.notification.telegram.TelegramBot;
import com.iotplatform.notification.telegram.TelegramClient;

import java.util.List;

/** Собирает NotificationService целиком: Telegram-бот, email-канал, диспетчер. */
public final class NotificationModule implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(NotificationModule.class.getName());

    private final NotificationService service;
    private final TelegramBot bot;
    private final NotificationDispatcher dispatcher;

    private NotificationModule(NotificationService service, TelegramBot bot, NotificationDispatcher dispatcher) {
        this.service = service;
        this.bot = bot;
        this.dispatcher = dispatcher;
    }

    public static NotificationModule create(NotificationConfig config, PlatformData platform, TelegramLinks links) {
        return create(config, platform, links, new SmtpEmailSender(config.email()));
    }

    public static NotificationModule create(NotificationConfig config, PlatformData platform,
                                            TelegramLinks links, EmailSender emailSender) {
        if (!config.telegramConfigured()) {
            LOG.log(System.Logger.Level.WARNING, "Telegram не настроен: не задан TELEGRAM_BOT_TOKEN");
        }
        EmailConfig email = config.email();
        if (!email.isConfigured()) {
            LOG.log(System.Logger.Level.INFO, "Email-канал выключен: не задан SMTP_HOST");
        } else if (!email.problems().isEmpty()) {
            LOG.log(System.Logger.Level.WARNING,
                    "Email-канал выключен из-за ошибок настройки: " + String.join("; ", email.problems()));
        }

        TelegramClient telegram = new TelegramClient(config.telegramToken());
        List<NotificationChannel> channels = List.of(
                new TelegramChannel(telegram, links),
                new EmailChannel(emailSender, new EmailRenderer(config), platform));
        NotificationDispatcher dispatcher = new NotificationDispatcher(channels);
        NotificationService service = new NotificationService(
                platform, dispatcher, config.defaultNorm(), config.defaultDeviation());
        TelegramBot bot = new TelegramBot(telegram, service, platform, links, config.zone());
        return new NotificationModule(service, bot, dispatcher);
    }

    public NotificationService service() {
        return service;
    }

    public void start() {
        bot.start();
    }

    @Override
    public void close() {
        bot.stop();
        dispatcher.close();
    }
}