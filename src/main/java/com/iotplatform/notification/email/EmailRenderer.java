package com.iotplatform.notification.email;

import com.iotplatform.notification.config.NotificationConfig;
import com.iotplatform.notification.model.NotificationEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Формирует письмо из события: подстановка данных устройства и события в HTML-шаблон. */
public class EmailRenderer {
    public static final String TEMPLATE_RESOURCE = "/notification/templates/email-notification.html";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final String FONT = "font-family:Arial,Helvetica,sans-serif;";

    private final NotificationConfig config;
    private final String template;

    public EmailRenderer(NotificationConfig config) {
        this(config, loadTemplate(TEMPLATE_RESOURCE));
    }

    public EmailRenderer(NotificationConfig config, String template) {
        this.config = config;
        this.template = template;
    }

    public RenderedEmail render(NotificationEvent e) {
        String eventTime = TIME_FORMAT.withZone(config.zone()).format(e.time())
                + " (" + config.zone().getId() + ")";
        String subject = (config.platformName() + ": " + e.type().title() + " — " + e.deviceName())
                .replaceAll("[\\r\\n]+", " ").trim();
        Optional<String> url = config.deviceUrl(e.deviceId());

        Map<String, String> escaped = new LinkedHashMap<>();
        escaped.put("subject", subject);
        escaped.put("preheader", e.description());
        escaped.put("platformName", config.platformName());
        escaped.put("badgeText", e.type().badge());
        escaped.put("accentColor", e.type().accentColor());
        escaped.put("title", e.type().title());
        escaped.put("deviceName", e.deviceName());
        escaped.put("description", e.description());
        escaped.put("eventTime", eventTime);

        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("detailsRows", detailsRows(e.details()));
        raw.put("deviceLinkBlock", url.map(EmailRenderer::linkBlock).orElse(""));

        String html = TemplateEngine.render(template, escaped, raw);
        String text = e.telegramText() + "\n\nВремя события: " + eventTime
                + url.map(u -> "\nУстройство: " + u).orElse("");
        return new RenderedEmail(subject, html, text);
    }

    private static String detailsRows(Map<String, String> details) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> d : details.entrySet()) {
            sb.append("<tr>")
                    .append("<td style=\"padding:10px 0;border-bottom:1px solid #e6e9ef;").append(FONT)
                    .append("font-size:14px;color:#5b6577;\">").append(TemplateEngine.escapeHtml(d.getKey()))
                    .append("</td>")
                    .append("<td align=\"right\" style=\"padding:10px 0;border-bottom:1px solid #e6e9ef;").append(FONT)
                    .append("font-size:14px;font-weight:bold;color:#1b2430;\">")
                    .append(TemplateEngine.escapeHtml(d.getValue())).append("</td>")
                    .append("</tr>");
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

    private static String loadTemplate(String resource) {
        try (InputStream in = EmailRenderer.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Не найден шаблон письма " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать шаблон письма " + resource, e);
        }
    }
}
