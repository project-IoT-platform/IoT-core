CREATE TABLE IF NOT EXISTS device (
                                      device_id        UUID PRIMARY KEY,
                                      user_id          UUID,
                                      name             TEXT NOT NULL,
                                      last_data_time   TIMESTAMPTZ,
                                      battery_level    SMALLINT,
                                      manufacturer     TEXT,
                                      state            TEXT,
                                      geolocation      TEXT
);

CREATE TABLE IF NOT EXISTS telemetry (
                                         event_id         UUID PRIMARY KEY,
                                         device_id        UUID NOT NULL REFERENCES device(device_id),
    time             TIMESTAMPTZ NOT NULL,
    telemetry_type   SMALLINT NOT NULL,
    value            DOUBLE PRECISION
    );
