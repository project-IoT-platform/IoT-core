package com.iotplatform.notification.email;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Подстановка в HTML-шаблон: {{имя}} подставляет текст с экранированием HTML (безопасно для данных пользователя),
 * {{{имя}}} подставляет готовый HTML (только для наших фрагментов). Нет значения: ошибка, чтобы опечатка не ушла в письмо.
 */
public final class TemplateEngine {
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{\\{\\s*(\\w+)\\s*\\}\\}\\}|\\{\\{\\s*(\\w+)\\s*\\}\\}");

    private TemplateEngine() {}

    public static String render(String template, Map<String, String> escaped, Map<String, String> raw) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key;
            String value;
            if (m.group(1) != null) {
                key = m.group(1);
                value = raw.get(key);
            } else {
                key = m.group(2);
                String v = escaped.get(key);
                value = v == null ? null : escapeHtml(v);
            }
            if (value == null) {
                throw new IllegalStateException("Для плейсхолдера «" + key + "» не передано значение");
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    public static String escapeHtml(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}