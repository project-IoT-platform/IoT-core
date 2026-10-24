package com.iotplatform.iotcore.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.iotplatform.iotcore.http.Json;

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

    public void sendMessage(long chatId, String text) {
        if (!isConfigured()) {
            LOG.log(System.Logger.Level.WARNING, "Telegram не настроен, сообщение не отправлено: {0}", text);
            return;
        }
        try {
            String body = Json.MAPPER.writeValueAsString(Map.of("chat_id", chatId, "text", text));
            HttpRequest request = HttpRequest.newBuilder(URI.create(API + token + "/sendMessage"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
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
        return Json.MAPPER.readTree(resp.body()).path("result");
    }
}