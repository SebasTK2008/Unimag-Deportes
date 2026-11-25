ALTER TABLE institutional_block
    ADD COLUMN status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN cancelled_at timestamptz,
    ADD COLUMN cancelled_by uuid REFERENCES app_user(id),
    ADD COLUMN cancellation_reason text,
    ADD CONSTRAINT institutional_block_cancellation_check CHECK (
        (status = 'ACTIVE' AND cancelled_at IS NULL AND cancelled_by IS NULL AND cancellation_reason IS NULL)
        OR
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL
            AND cancellation_reason IS NOT NULL
            AND char_length(cancellation_reason) BETWEEN 1 AND 1000
            AND char_length(btrim(cancellation_reason)) > 0
            AND cancellation_reason = btrim(cancellation_reason))
    );

CREATE INDEX institutional_block_space_status_start_id_idx
    ON institutional_block (space_id, status, start_at, id);
