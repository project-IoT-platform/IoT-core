package com.iotplatform.notification.channel;

import com.iotplatform.notification.data.PlatformData;
import com.iotplatform.notification.email.EmailRenderer;
import com.iotplatform.notification.email.EmailSender;
import com.iotplatform.notification.exception.DeliveryException;
import com.iotplatform.notification.model.Notification;
import com.iotplatform.notification.model.User;

import java.util.Optional;

/** Отправляет письмо на email владельца устройства (только для типов с byEmail). */
public class EmailChannel implements Channel {
    private final EmailSender sender;
    private final EmailRenderer renderer;
    private final PlatformData platform;

    public EmailChannel(EmailSender sender, EmailRenderer renderer, PlatformData platform) {
        this.sender = sender;
        this.renderer = renderer;
        this.platform = platform;
    }

    @Override
    public String name() {
        return "email";
    }

    @Override
    public boolean enabled() {
        return sender.isConfigured();
    }

    @Override
    public Status send(Notification n) throws DeliveryException {
        if (!n.type().byEmail()) return Status.SKIPPED;
        Optional<User> user = platform.findUser(n.userId());
        if (user.isEmpty() || user.get().email() == null || user.get().email().isBlank()) {
            return Status.NO_RECIPIENT;
        }
        EmailRenderer.Mail mail;
        try {
            mail = renderer.render(n);
        } catch (RuntimeException e) {
            throw new DeliveryException("Не удалось сформировать письмо: " + e.getMessage(), e);
        }
        sender.send(user.get().email(), mail.subject(), mail.html(), mail.text());
        return Status.SENT;
    }
}