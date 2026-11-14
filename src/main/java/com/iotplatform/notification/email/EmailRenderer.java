package com.iotplatform.notification.email;

import com.iotplatform.notification.config.Config;
import com.iotplatform.notification.model.Notification;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Превращает уведомление в письмо: тема, HTML (по шаблону из файла) и текстовая версия. */
public class EmailRenderer {
    public record Mail(String subject, String html, String text) {}

    private static final String TEMPLATE_RESOURCE = "/templates/email-notification.html";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final String FONT = "font-family:Arial,Helvetica,sans-serif;";

    private final Config config;
    private final String template;

    public EmailRenderer(Config config) {
        this.config = config;
        this.template = loadTemplate();
    }

    public Mail render(Notification n) {
        String eventTime = TIME_FORMAT.withZone(config.zone).format(n.time()) + " (" + config.zone.getId() + ")";
        // у письма о регистрации пользователя устройства нет: тогда без « — имя устройства»
        String subject = (config.platformName + ": " + n.type().title()
                + (n.deviceName().isBlank() ? "" : " — " + n.deviceName()))
                .replaceAll("[\\r\\n]+", " ").trim();
        Optional<String> url = config.deviceUrl(n.deviceId());

        Map<String, String> escaped = new LinkedHashMap<>();   // подставляются с экранированием
        escaped.put("subject", subject);
        escaped.put("preheader", n.description());
        escaped.put("platformName", config.platformName);
        escaped.put("badgeText", n.type().badge());
        escaped.put("accentColor", n.type().accentColor());
        escaped.put("title", n.type().title());
        escaped.put("description", n.description());
        escaped.put("eventTime", eventTime);

        Map<String, String> raw = new LinkedHashMap<>();   // готовые HTML-фрагменты
        raw.put("deviceLine", n.deviceName().isBlank() ? ""
                : "Устройство: <b>" + TemplateEngine.escapeHtml(n.deviceName()) + "</b>");
        raw.put("detailsRows", detailsRows(n.details()));
        raw.put("deviceLinkBlock", url.map(EmailRenderer::linkBlock).orElse(""));

        String html = TemplateEngine.render(template, escaped, raw);
        String text = n.telegramText() + "\n\nВремя события: " + eventTime
                + url.map(u -> "\nУстройство: " + u).orElse("");
        return new Mail(subject, html, text);
    }

    private static String detailsRows(Map<String, String> details) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> d : details.entrySet()) {
            sb.append("<tr><td style=\"padding:10px 0;border-bottom:1px solid #e6e9ef;").append(FONT)
                    .append("font-size:14px;color:#5b6577;\">").append(TemplateEngine.escapeHtml(d.getKey()))
                    .append("</td><td align=\"right\" style=\"padding:10px 0;border-bottom:1px solid #e6e9ef;")
                    .append(FONT).append("font-size:14px;font-weight:bold;color:#1b2430;\">")
                    .append(TemplateEngine.escapeHtml(d.getValue())).append("</td></tr>");
        }
        return sb.toString();
    }

    private static String linkBlock(String url) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-top:28px;\">"
                + "<tr><td bgcolor=\"#0b1f4b\" style=\"background-color:#0b1f4b;border-radius:6px;\">"
                + "<a href=\"" + TemplateEngine.escapeHtml(url) + "\" target=\"_blank\" "
                + "style=\"display:inline-block;padding:12px 26px;" + FONT
                + "font-size:14px;font-weight:bold;color:#ffffff;text-decoration:none;\">Открыть устройство</a>"
                + "</td></tr></table>";
    }

    private static String loadTemplate() {
        try (InputStream in = EmailRenderer.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (in == null) throw new IllegalStateException("Не найден шаблон письма " + TEMPLATE_RESOURCE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать шаблон письма", e);
        }
    }
}