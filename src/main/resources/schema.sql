-- ЧАСТЬ ЛЁШИ:
--  users, device, telemetry.
-- в общем, это просто нанейрослопленный файл и он просто существует как черновик, его надо подогнать под тот шаблон бд, который у тебя

CREATE TABLE IF NOT EXISTS users (
                                     id             UUID PRIMARY KEY,
                                     username       TEXT NOT NULL,
                                     email          TEXT NOT NULL,
                                     phone          TEXT,
                                     date_of_birth  DATE,
                                     password_hash  BYTEA NOT NULL,
                                     role           TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS device (
                                      device_id       UUID PRIMARY KEY,
                                      user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    name            TEXT NOT NULL,
    last_data_time  TIMESTAMPTZ,
    battery_level   SMALLINT,
    manufacturer    TEXT,
    state           TEXT,
    geolocation     TEXT
    );

CREATE TABLE IF NOT EXISTS telemetry (
                                         event_id        UUID PRIMARY KEY,
                                         device_id       UUID NOT NULL REFERENCES device(device_id) ON DELETE CASCADE,
    time            TIMESTAMPTZ NOT NULL,
    telemetry_type  SMALLINT NOT NULL,
    value           DOUBLE PRECISION
    );
