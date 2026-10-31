package com.iotplatform.notification.email;

import com.iotplatform.notification.config.EmailConfig;
import com.iotplatform.notification.config.EmailConfig.Security;
import com.iotplatform.notification.exception.NotificationDeliveryException;
import com.iotplatform.notification.exception.NotificationDeliveryException.Reason;
import jakarta.mail.Authenticator;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Properties;

/** Отправка писем по SMTP (Jakarta Mail). Пароль нигде не логируется. */
public class SmtpEmailSender implements EmailSender {
    private final EmailConfig config;
    private final Session session;   // null, если SMTP не настроен

    public SmtpEmailSender(EmailConfig config) {
        this.config = config;
        this.session = config.isUsable() ? createSession(config) : null;
    }

    @Override
    public boolean isConfigured() {
        return session != null;
    }

    @Override
    public void send(EmailMessage message) throws NotificationDeliveryException {
        if (session == null) {
            throw new NotificationDeliveryException(Reason.NOT_CONFIGURED,
                    "SMTP не настроен: " + String.join("; ", config.problems()));
        }
        try {
            Transport.send(build(message));
        } catch (AddressException e) {
            throw new NotificationDeliveryException(Reason.REJECTED_RECIPIENT,
                    "Некорректный адрес: " + e.getMessage(), e);
        } catch (AuthenticationFailedException e) {
            throw new NotificationDeliveryException(Reason.AUTHENTICATION,
                    "SMTP: сервер отклонил логин или пароль (" + rootMessage(e) + ")", e);
        } catch (SendFailedException e) {
            throw new NotificationDeliveryException(Reason.REJECTED_RECIPIENT,
                    "SMTP: сервер отклонил письмо или адрес получателя (" + rootMessage(e) + ")", e);
        } catch (MessagingException e) {
            throw new NotificationDeliveryException(Reason.CONNECTION,
                    "SMTP: не удалось отправить письмо на " + config.host() + ":" + config.port()
                            + " (" + rootMessage(e) + ")", e);
        } catch (UnsupportedEncodingException e) {
            throw new NotificationDeliveryException(Reason.OTHER, "Ошибка кодировки письма", e);
        }
    }

    private MimeMessage build(EmailMessage m) throws MessagingException, UnsupportedEncodingException {
        String rawTo = m.to();
        if (rawTo == null || rawTo.isBlank() || rawTo.contains("\r") || rawTo.contains("\n")
                || !rawTo.contains("@")) {
            throw new AddressException("пустой адрес, нет «@» или перевод строки в адресе", String.valueOf(rawTo));
        }
        InternetAddress to = new InternetAddress(rawTo.trim(), true);   // строгий разбор одного адреса
        to.validate();

        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(config.fromAddress(), config.fromName(), "UTF-8"));
        msg.setRecipient(MimeMessage.RecipientType.TO, to);
        msg.setSubject(oneLine(m.subject()), "UTF-8");
        msg.setSentDate(new Date());
        msg.setHeader("Auto-Submitted", "auto-generated");

        MimeBodyPart text = new MimeBodyPart();
        text.setText(m.textBody(), "UTF-8");
        MimeBodyPart html = new MimeBodyPart();
        html.setText(m.htmlBody(), "UTF-8", "html");
        MimeMultipart alternative = new MimeMultipart("alternative");   // клиент выберет лучшую (последнюю) часть
        alternative.addBodyPart(text);
        alternative.addBodyPart(html);
        msg.setContent(alternative);
        msg.saveChanges();
        return msg;
    }

    private static Session createSession(EmailConfig c) {
        Properties p = new Properties();
        p.put("mail.smtp.host", c.host());
        p.put("mail.smtp.port", String.valueOf(c.port()));
        p.put("mail.smtp.connectiontimeout", String.valueOf(c.timeoutMillis()));
        p.put("mail.smtp.timeout", String.valueOf(c.timeoutMillis()));
        p.put("mail.smtp.writetimeout", String.valueOf(c.timeoutMillis()));
        p.put("mail.smtp.from", c.fromAddress());   // адрес для возвратов (envelope sender)
        p.put("mail.smtp.ssl.checkserveridentity", "true");
        if (c.security() == Security.STARTTLS) {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "true");
        } else if (c.security() == Security.SSL) {
            p.put("mail.smtp.ssl.enable", "true");
        }
        if (c.username() == null) {
            return Session.getInstance(p);
        }
        p.put("mail.smtp.auth", "true");
        return Session.getInstance(p, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(c.username(), c.password());
            }
        });
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String msg = root.getMessage();
        return msg != null ? msg : root.getClass().getSimpleName();
    }
}
