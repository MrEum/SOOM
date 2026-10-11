-- Fresh installations only. For an existing table, inspect SHOW CREATE TABLE `user`
-- and reconcile column types and unique constraints before applying changes.
CREATE TABLE IF NOT EXISTS `user` (
    user_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    nickname VARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    email_verified_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    deleted_at DATETIME NULL,
    scheduled_delete_at DATETIME NULL,
    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT uk_user_nickname UNIQUE (nickname)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
