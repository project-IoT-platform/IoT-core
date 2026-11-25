package com.iotplatform.iotcore.http;

import com.iotplatform.iotcore.dto.DeviceLimitsDto;
import com.iotplatform.iotcore.repository.PostgresDeviceLimitsRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationDataApiTest {
    private static final String TOKEN = "test-service-key";
    private static final String AUTHORIZATION = "Bearer " + TOKEN;
    private static final UUID DEVICE = UUID.randomUUID();
    private static final String PATH = "/internal/notifications/devices/" + DEVICE + "/limits";

    @Test
    void internalRoutesRequireServiceAuthenticationBeforeParsingInput() {
        var router = router(TOKEN, null);
        String path = "/internal/notifications/devices/bad/limits";
        assertEquals(401, router.handle("PUT", path, null, "Bearer wrong").status());
        assertEquals(401, router.handle("PUT", path, null).status());
        assertEquals(400, router.handle("PUT", path, null, AUTHORIZATION).status());
    }

    @Test
    void missingConfigurationDisablesInternalRoutes() {
        assertEquals(404, router(null, null).handle("GET", PATH, null, AUTHORIZATION).status());
    }

    @Test
    void limitUpdatesDistinguishMissingListFromEmptyList() throws Exception {
        var limits = new PostgresDeviceLimitsRepository(null) {
            @Override
            public DeviceLimitsDto replace(UUID id, DeviceLimitsDto dto) {
                assertEquals(DEVICE, id);
                return dto;
            }
        };
        var router = router(TOKEN, limits);
        var untouched = router.handle("PUT", PATH, json("{\"batteryLimit\":25}"), AUTHORIZATION);
        var cleared = router.handle("PUT", PATH, json("{\"telemetryLimits\":[]}"), AUTHORIZATION);
        assertEquals(200, untouched.status());
        assertEquals(200, cleared.status());
        assertTrue(Json.MAPPER.readTree(untouched.body()).get("telemetryLimits").isNull());
        assertEquals(0, Json.MAPPER.readTree(cleared.body()).get("telemetryLimits").size());
        assertEquals(400, router.handle("PUT", PATH, json("{\"batteryLimit\":1.5}"), AUTHORIZATION).status());
    }

    @Test
    void invalidLimitsAreRejectedBeforeDatabaseAccess() {
        var router = router(TOKEN, new PostgresDeviceLimitsRepository(null));
        assertEquals(400, router.handle("PUT", PATH, json("{\"batteryLimit\":101}"), AUTHORIZATION).status());
        assertEquals(400, router.handle("PUT", PATH,
                json("{\"telemetryLimits\":[{\"telemetryType\":1,\"lowerBound\":2,\"upperBound\":1}]}"),
                AUTHORIZATION).status());
    }

    private static ApiRouter router(String token, PostgresDeviceLimitsRepository limits) {
        return new ApiRouter(null, null, null, null, null, new NotificationDataApi(token, null, limits));
    }

    private static byte[] json(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
