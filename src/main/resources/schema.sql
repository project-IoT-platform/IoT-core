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

-- НОВОЕ: можно ли отправить уведомление о низком заряде.
-- TRUE: уведомление ещё не отправляли (или устройство зарядили выше нижней границы).
-- FALSE: уже отправили, повторно не шлём, пока заряд не поднимется выше нижней границы.
ALTER TABLE device ADD COLUMN IF NOT EXISTS battery_notify_allowed BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS telemetry (
                                         id UUID PRIMARY KEY,
                                         device_id UUID NOT NULL,
                                         recorded_at TIMESTAMPTZ NOT NULL,
                                         telemetry_type SMALLINT NOT NULL,
                                         value FLOAT NOT NULL,

                                         CONSTRAINT telemetry_device_fkey
                                         FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
    );

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

-- НОВОЕ: строгое правило (TRUE) отклоняет показание с HTTP 400, в БД оно не пишется.
-- Нестрогое (FALSE): показание сохраняется (HTTP 202) и проверяется для уведомления.
ALTER TABLE rule ADD COLUMN IF NOT EXISTS is_strict BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS device_to_rule (
                                              id UUID PRIMARY KEY,
                                              rule_id UUID NOT NULL REFERENCES rule(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    UNIQUE (rule_id, device_id)
    );