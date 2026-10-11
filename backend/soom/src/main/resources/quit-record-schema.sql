-- Idempotency receipts only. The existing no_smoking_record table is unchanged.
-- Receipts survive record deletion to prevent replaying a completed import.
CREATE TABLE IF NOT EXISTS quit_record_import (
    user_id INT NOT NULL,
    client_id VARCHAR(36) NOT NULL,
    record_id BIGINT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, client_id)
);
