package com.iotplatform.notification.email;

import com.iotplatform.notification.config.Config;
import com.iotplatform.notification.exception.DeliveryException;
import jakarta.mail.Authenticator;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Message;
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

/** Отправка писем по SMTP через Jakarta Mail: текстовая и HTML-части, везде UTF-8. Пароль нигде не логируется. */
public class SmtpEmailSender implements EmailSender {
    private final Config config;
    private final Session session;   // null, если SMTP не настроен

    public SmtpEmailSender(Config config) {
        this.config = config;
        this.session = config.smtpUsable() ? createSession(config) : null;
    }

    @Override
    public boolean isConfigured() {
        return session != null;
    }

    @Override
    public void send(String to, String subject, String html, String text) throws DeliveryException {
        if (session == null) {
            throw new DeliveryException("SMTP не настроен: " + String.join("; ", config.smtpProblems()));
        }
        try {
            Transport.send(build(to, subject, html, text));
        } catch (AddressException e) {
            throw new DeliveryException("Некорректный адрес получателя: " + e.getMessage(), e);
        } catch (AuthenticationFailedException e) {
            throw new DeliveryException("SMTP: сервер отклонил логин или пароль (" + root(e) + ")", e);
        } catch (SendFailedException e) {
            throw new DeliveryException("SMTP: сервер отклонил письмо или адрес получателя (" + root(e) + ")", e);
        } catch (MessagingException e) {
            throw new DeliveryException("SMTP: не удалось отправить письмо на " + config.smtpHost + ":"
                    + config.smtpPort + " (" + root(e) + ")", e);
        } catch (UnsupportedEncodingException e) {
            throw new DeliveryException("Ошибка кодировки письма", e);
        }
    }

    private MimeMessage build(String to, String subject, String html, String text)
            throws MessagingException, UnsupportedEncodingException {
        // проверка адреса до обращения к сети; перевод строки в адресе запрещён (защита от подмены заголовков)
        if (to == null || to.isBlank() || to.contains("\r") || to.contains("\n") || !to.contains("@")) {
            throw new AddressException("пустой адрес, нет «@» или перевод строки", String.valueOf(to));
        }
        InternetAddress recipient = new InternetAddress(to.trim(), true);
        recipient.validate();

        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(config.smtpFrom, config.smtpFromName, "UTF-8"));
        msg.setRecipient(Message.RecipientType.TO, recipient);
        msg.setSubject(subject == null ? "" : subject.replaceAll("[\\r\\n]+", " ").trim(), "UTF-8");
        msg.setSentDate(new Date());
        msg.setHeader("Auto-Submitted", "auto-generated");

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(text, "UTF-8");
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setText(html, "UTF-8", "html");
        MimeMultipart alternative = new MimeMultipart("alternative");   // клиент выбирает лучшую часть (HTML)
        alternative.addBodyPart(textPart);
        alternative.addBodyPart(htmlPart);
        msg.setContent(alternative);
        msg.saveChanges();
        return msg;
    }

    private static Session createSession(Config c) {
        Properties p = new Properties();
        p.put("mail.smtp.host", c.smtpHost);
        p.put("mail.smtp.port", String.valueOf(c.smtpPort));
        p.put("mail.smtp.connectiontimeout", String.valueOf(c.smtpTimeoutMs));
        p.put("mail.smtp.timeout", String.valueOf(c.smtpTimeoutMs));
        p.put("mail.smtp.writetimeout", String.valueOf(c.smtpTimeoutMs));
        p.put("mail.smtp.from", c.smtpFrom);
        p.put("mail.smtp.ssl.checkserveridentity", "true");
        if (c.smtpSecurity.equals("STARTTLS")) {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "true");
        } else if (c.smtpSecurity.equals("SSL")) {
            p.put("mail.smtp.ssl.enable", "true");
        }
        if (c.smtpUser == null) {
            return Session.getInstance(p);
        }
        p.put("mail.smtp.auth", "true");
        return Session.getInstance(p, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(c.smtpUser, c.smtpPassword);
            }
        });
    }

    private static String root(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        return r.getMessage() != null ? r.getMessage() : r.getClass().getSimpleName();
    }
}