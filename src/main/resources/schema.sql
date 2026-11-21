DO $$
BEGIN
IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'role') THEN
CREATE TYPE role AS ENUM ('ADMIN', 'GUEST');
END IF;

IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'state') THEN
CREATE TYPE state AS ENUM ('ON', 'OFF');
END IF;
END $$;

CREATE TABLE IF NOT EXISTS users (
                                     id UUID PRIMARY KEY,
                                     username TEXT NOT NULL,
                                     email TEXT NOT NULL,
                                     phone TEXT,
                                     birthdate DATE,
                                     password_hash BYTEA NOT NULL,
                                     user_role role NOT NULL
);

-- подтверждена ли регистрация (ID из письма введён)
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_confirmed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS device (
                                      id UUID PRIMARY KEY,
                                      user_id UUID,
                                      name TEXT NOT NULL,
                                      last_trans TIMESTAMPTZ,
                                      charge_level SMALLINT,
                                      manufacturer TEXT,
                                      device_state state NOT NULL,
                                      geolocation TEXT,

                                      CONSTRAINT device_user_id_fkey
                                      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
    );

ALTER TABLE device ADD COLUMN IF NOT EXISTS battery_notify_allowed BOOLEAN NOT NULL DEFAULT TRUE;

-- лимиты устройства: нижняя граница заряда и общие значения нормы
CREATE TABLE IF NOT EXISTS rute (device_id UUID PRIMARY KEY REFERENCES device(id) ON DELETE CASCADE, battery_limit INTEGER, norm DOUBLE PRECISION, deviation DOUBLE PRECISION);

CREATE TABLE IF NOT EXISTS telemetry (
                                         id UUID PRIMARY KEY,
                                         device_id UUID NOT NULL,
                                         recorded_at TIMESTAMPTZ NOT NULL,
                                         telemetry_type SMALLINT NOT NULL,
                                         value FLOAT NOT NULL,

                                         CONSTRAINT telemetry_device_fkey
                                         FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
    );

-- НОВОЕ: индекс под выборки телеметрии конкретного типа по времени (график, среднее, последнее значение)
CREATE INDEX IF NOT EXISTS telemetry_device_type_time_idx
    ON telemetry (device_id, telemetry_type, recorded_at DESC);

CREATE TABLE IF NOT EXISTS telegram_chats (
                                              user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    chat_id BIGINT NOT NULL UNIQUE
    );

DO $$
BEGIN
IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'rule_operator') THEN
CREATE TYPE rule_operator AS ENUM ('GE', 'LE', 'GT', 'LT', 'EQ', 'NE');
END IF;
END $$;

CREATE TABLE IF NOT EXISTS rule (
                                    id UUID PRIMARY KEY,
                                    telemetry_type SMALLINT NOT NULL,
                                    operator rule_operator NOT NULL,
                                    value FLOAT NOT NULL
);

-- is_strict = TRUE по умолчанию: правило отклоняет показание с HTTP 400 и в БД не пишется.
-- is_strict = FALSE: показание сохраняется (HTTP 202) и проверяется для уведомления.
ALTER TABLE rule ADD COLUMN IF NOT EXISTS is_strict BOOLEAN NOT NULL DEFAULT TRUE;
-- На случай, если колонка уже была создана со старым DEFAULT FALSE:
ALTER TABLE rule ALTER COLUMN is_strict SET DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS device_to_rule (
                                              id UUID PRIMARY KEY,
                                              rule_id UUID NOT NULL REFERENCES rule(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    UNIQUE (rule_id, device_id)
    );