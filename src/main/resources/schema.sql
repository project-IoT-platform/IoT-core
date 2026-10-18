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
    id UUID PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    name TEXT NOT NULL,
    last_trans TIMESTAMPTZ,
    charge_level SMALLINT,
    manufacturer TEXT,
    device_state state NOT NULL,
    geolocation TEXT,

    CONSTRAINT device_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;
);

CREATE TABLE IF NOT EXISTS telemetry (
    id UUID PRIMARY KEY NOT NULL,
    device_id UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    telemetry_type SMALLINT NOT NULL,
    value FLOAT NOT NULL,

    CONSTRAINT telemetry_device_fkey
        FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE SET NULL;
);