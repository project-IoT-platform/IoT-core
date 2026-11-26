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

ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_hash BYTEA;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_expires TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_sent TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS device (
                                      id UUID PRIMARY KEY,
                                      user_id UUID,
                                      name TEXT NOT NULL,
                                      last_trans TIMESTAMPTZ,
                                      charge_level SMALLINT,
                                      manufacturer TEXT,
                                      device_state state NOT NULL,
                                      geolocation TEXT,
                                      battery_notify_allowed BOOLEAN NOT NULL DEFAULT TRUE,

                                      CONSTRAINT device_user_id_fkey
                                      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
    );

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
                                    value FLOAT NOT NULL,
                                    is_strict BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS device_to_rule (
                                              id UUID PRIMARY KEY,
                                              rule_id UUID NOT NULL REFERENCES rule(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    UNIQUE (rule_id, device_id)
    );

CREATE TABLE IF NOT EXISTS notification_defaults (id INT PRIMARY KEY, norm DOUBLE PRECISION NOT NULL, deviation DOUBLE PRECISION NOT NULL);
INSERT INTO notification_defaults(id,norm,deviation) VALUES (1,25,5) ON CONFLICT(id) DO NOTHING;
CREATE TABLE IF NOT EXISTS notification_preferences (user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE, channel TEXT NOT NULL, interval_seconds INTEGER NOT NULL CHECK(interval_seconds>=0), PRIMARY KEY(user_id,channel));
CREATE TABLE IF NOT EXISTS notification_buffer (id BIGSERIAL PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE, channel TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), type TEXT NOT NULL, device_id UUID, device_name TEXT, event_time TIMESTAMPTZ NOT NULL, description TEXT, details TEXT NOT NULL, telegram_text TEXT NOT NULL);
CREATE INDEX IF NOT EXISTS notification_buffer_group_idx ON notification_buffer(user_id,channel,created_at);
CREATE INDEX IF NOT EXISTS notification_buffer_created_idx ON notification_buffer(created_at);

ALTER TABLE device ADD COLUMN IF NOT EXISTS notification_battery_limit INTEGER CHECK(notification_battery_limit BETWEEN 0 AND 100);

CREATE TABLE IF NOT EXISTS notification_outbox (
                                                   id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                                   event_id UUID NOT NULL UNIQUE,
                                                   event_type TEXT NOT NULL,
                                                   envelope JSONB NOT NULL
);
