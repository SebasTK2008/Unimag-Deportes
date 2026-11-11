CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE app_user (
    id uuid PRIMARY KEY, email varchar(254) NOT NULL UNIQUE,
    name varchar(200), suspended_until timestamptz,
    role varchar(20) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    created_at timestamptz NOT NULL,
    CHECK (email = lower(trim(email)))
);
CREATE TABLE login_code (
    id uuid PRIMARY KEY, email varchar(254) NOT NULL, code_hash varchar(128) NOT NULL,
    expires_at timestamptz NOT NULL, attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    used_at timestamptz, created_at timestamptz NOT NULL
);
CREATE INDEX login_code_email_created_idx ON login_code (email, created_at);
CREATE TABLE login_code_request_ip (
    id uuid PRIMARY KEY, ip_address varchar(45) NOT NULL, created_at timestamptz NOT NULL
);
CREATE INDEX login_code_request_ip_created_idx ON login_code_request_ip (ip_address, created_at);
CREATE TABLE space (
    id uuid PRIMARY KEY, name varchar(200) NOT NULL, capacity integer NOT NULL CHECK (capacity > 0),
    requirements text, opening_time time NOT NULL, closing_time time NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('ACTIVE', 'MAINTENANCE')),
    CHECK (closing_time > opening_time)
);
CREATE TABLE space_discipline (
    space_id uuid NOT NULL REFERENCES space(id), discipline varchar(20) NOT NULL
        CHECK (discipline IN ('BASKETBALL', 'VOLLEYBALL', 'FOOTBALL_5', 'FOOTBALL_11', 'TENNIS')),
    PRIMARY KEY (space_id, discipline)
);
CREATE TABLE reservation (
    id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES app_user(id),
    space_id uuid NOT NULL REFERENCES space(id), start_at timestamptz NOT NULL,
    end_at timestamptz NOT NULL, status varchar(32) NOT NULL CHECK (status IN
        ('CONFIRMED', 'IN_USE', 'COMPLETED', 'CANCELLED_ON_TIME', 'CANCELLED_LATE',
         'NO_SHOW', 'CANCELLED_BY_ADMIN', 'CANCELLED_BY_SUSPENSION')),
    created_at timestamptz NOT NULL, checked_in_at timestamptz, reminder_sent_at timestamptz,
    cancellation_threshold_minutes integer NOT NULL CHECK (cancellation_threshold_minutes >= 0),
    checkin_opening_minutes integer NOT NULL CHECK (checkin_opening_minutes >= 0),
    CHECK (end_at > start_at),
    EXCLUDE USING gist (space_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&)
        WHERE (status IN ('CONFIRMED', 'IN_USE', 'COMPLETED', 'NO_SHOW')),
    EXCLUDE USING gist (user_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&)
        WHERE (status IN ('CONFIRMED', 'IN_USE', 'COMPLETED', 'NO_SHOW'))
);
CREATE TABLE institutional_block (
    id uuid PRIMARY KEY, space_id uuid NOT NULL REFERENCES space(id),
    type varchar(20) NOT NULL CHECK (type IN ('CLASS', 'EVENT', 'MAINTENANCE')),
    reason text NOT NULL, start_at timestamptz NOT NULL, end_at timestamptz NOT NULL,
    recurrence_group uuid, CHECK (end_at > start_at)
);
CREATE INDEX institutional_block_space_start_idx ON institutional_block (space_id, start_at);
CREATE TABLE equipment (
    id uuid PRIMARY KEY, name varchar(200) NOT NULL UNIQUE, discipline varchar(20)
        CHECK (discipline IN ('BASKETBALL', 'VOLLEYBALL', 'FOOTBALL_5', 'FOOTBALL_11', 'TENNIS')),
    stock integer NOT NULL CHECK (stock >= 0), status varchar(20) NOT NULL
        CHECK (status IN ('AVAILABLE', 'INACTIVE'))
);
CREATE TABLE reservation_equipment (
    reservation_id uuid NOT NULL REFERENCES reservation(id), equipment_id uuid NOT NULL REFERENCES equipment(id),
    quantity integer NOT NULL CHECK (quantity > 0), PRIMARY KEY (reservation_id, equipment_id)
);
CREATE TABLE usage_parameters (
    id uuid PRIMARY KEY,
    version_number bigint NOT NULL UNIQUE CHECK (version_number > 0),
    weekly_quota_hours integer NOT NULL CHECK (weekly_quota_hours > 0),
    no_show_threshold integer NOT NULL CHECK (no_show_threshold > 0),
    created_at timestamptz NOT NULL,
    created_by uuid REFERENCES app_user(id),
    CHECK ((version_number = 1 AND created_by IS NULL) OR (version_number > 1 AND created_by IS NOT NULL))
);
CREATE TABLE usage_parameters_head (
    id integer PRIMARY KEY CHECK (id = 1),
    current_version_id uuid NOT NULL REFERENCES usage_parameters(id)
);
INSERT INTO usage_parameters (id, version_number, weekly_quota_hours, no_show_threshold, created_at, created_by)
VALUES ('00000000-0000-0000-0000-000000000001', 1, 4, 3, now(), NULL);
INSERT INTO usage_parameters_head (id, current_version_id)
VALUES (1, '00000000-0000-0000-0000-000000000001');

CREATE FUNCTION reject_usage_parameters_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Usage parameter versions are immutable' USING ERRCODE = '23000';
END;
$$;
CREATE TRIGGER usage_parameters_immutable BEFORE UPDATE OR DELETE ON usage_parameters
FOR EACH ROW EXECUTE FUNCTION reject_usage_parameters_mutation();
CREATE TABLE audit_log (
    id uuid PRIMARY KEY, action varchar(64) NOT NULL, entity_type varchar(64) NOT NULL,
    entity_id uuid, user_id uuid REFERENCES app_user(id), detail text, created_at timestamptz NOT NULL
);
CREATE TABLE monthly_suspension (
    user_id uuid NOT NULL REFERENCES app_user(id), absence_month date NOT NULL,
    created_at timestamptz NOT NULL, PRIMARY KEY (user_id, absence_month),
    CHECK (EXTRACT(DAY FROM absence_month) = 1)
);
