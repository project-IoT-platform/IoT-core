package com.iotplatform.notification.channel;

import com.iotplatform.notification.email.EmailMessage;
import com.iotplatform.notification.email.EmailRenderer;
import com.iotplatform.notification.email.EmailSender;
import com.iotplatform.notification.email.RenderedEmail;
import com.iotplatform.notification.exception.NotificationDeliveryException;
import com.iotplatform.notification.exception.NotificationDeliveryException.Reason;
import com.iotplatform.notification.model.NotificationEvent;
import com.iotplatform.notification.port.PlatformData;
import com.iotplatform.notification.port.PlatformData.UserInfo;

import java.util.Optional;

public class EmailChannel implements NotificationChannel {
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
    public boolean isEnabled() {
        return sender.isConfigured();
    }

    @Override
    public DeliveryStatus send(NotificationEvent event) throws NotificationDeliveryException {
        if (!event.type().sendByEmail()) return DeliveryStatus.NOT_APPLICABLE;

        Optional<UserInfo> user = platform.findUser(event.recipientUserId());
        if (user.isEmpty() || user.get().email() == null || user.get().email().isBlank()) {
            return DeliveryStatus.NO_RECIPIENT;
        }

        RenderedEmail mail;
        try {
            mail = renderer.render(event);
        } catch (RuntimeException e) {
            throw new NotificationDeliveryException(Reason.TEMPLATE,
                    "Не удалось сформировать письмо: " + e.getMessage(), e);
        }
        sender.send(new EmailMessage(user.get().email(), mail.subject(), mail.html(), mail.text()));
        return DeliveryStatus.SENT;
    }
}