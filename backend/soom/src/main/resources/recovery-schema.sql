CREATE TABLE IF NOT EXISTS password_reset_token (
    user_id INT NOT NULL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS email_recovery_hint (
    user_id INT NOT NULL,
    question_number INT NOT NULL,
    answer_hash VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, question_number)
);

CREATE TABLE IF NOT EXISTS email_recovery_attempt (
    nickname_hash VARCHAR(64) NOT NULL PRIMARY KEY,
    attempts INT NOT NULL,
    window_started_at TIMESTAMP NOT NULL
);
