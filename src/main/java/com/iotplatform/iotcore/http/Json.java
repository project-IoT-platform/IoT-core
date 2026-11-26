package com.iotplatform.iotcore.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Общий настроенный {@link com.fasterxml.jackson.databind.ObjectMapper} проекта.
 */
public final class Json {
    /**
     * Маппер JSON: поддерживает типы {@code java.time}, пишет даты строками ISO, а не числами,
     * и не падает на неизвестных полях при чтении.
     */
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Json() {}
}
