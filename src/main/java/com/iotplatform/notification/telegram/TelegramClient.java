package com.iotplatform.notification.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class TelegramClient {
    private static final System.Logger LOG = System.getLogger(TelegramClient.class.getName());
    private static final String API = "https://api.telegram.org/bot";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String token;

    public TelegramClient(String token) {
        this.token = token;
    }

    public boolean isConfigured() {
        return token != null && !token.isBlank();
    }

    /** Асинхронная отправка (ответы бота). Поведение как в IoT Core. */
    public void sendMessage(long chatId, String text) {
        if (!isConfigured()) {
            LOG.log(System.Logger.Level.WARNING, "Telegram не настроен, сообщение не отправлено: {0}", text);
            return;
        }
        try {
            http.sendAsync(sendRequest(chatId, text), HttpResponse.BodyHandlers.ofString())
                    .whenComplete((resp, err) -> {
                        if (err != null) {
                            LOG.log(System.Logger.Level.ERROR, "Не удалось отправить сообщение в Telegram", err);
                        } else if (resp.statusCode() != 200) {
                            LOG.log(System.Logger.Level.ERROR,
                                    "Telegram вернул {0}: {1}", resp.statusCode(), resp.body());
                        }
                    });
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Ошибка при отправке в Telegram", e);
        }
    }

    /** Синхронная отправка для канала уведомлений: ошибка приходит исключением. */
    public void sendMessageBlocking(long chatId, String text) throws IOException, InterruptedException {
        if (!isConfigured()) {
            throw new IOException("не задан TELEGRAM_BOT_TOKEN");
        }
        HttpResponse<String> resp = http.send(sendRequest(chatId, text), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Telegram вернул HTTP " + resp.statusCode() + ": " + resp.body());
        }
    }

    public JsonNode getUpdates(long offset) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(API + token + "/getUpdates?timeout=25&offset=" + offset))
                .timeout(Duration.ofSeconds(35))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Telegram getUpdates: HTTP " + resp.statusCode());
        }
        return MAPPER.readTree(resp.body()).path("result");
    }

    private HttpRequest sendRequest(long chatId, String text) throws IOException {
        String body = MAPPER.writeValueAsString(Map.of("chat_id", chatId, "text", text));
        try {
            return HttpRequest.newBuilder(URI.create(API + token + "/sendMessage"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new IOException("некорректный формат токена Telegram");
        }
    }
}