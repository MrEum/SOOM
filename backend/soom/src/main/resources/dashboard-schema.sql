-- Existing business tables are reused; these tables add request deduplication,
-- categories and binary images without changing existing user records.
CREATE TABLE IF NOT EXISTS dashboard_request (
 user_id INT NOT NULL, request_id VARCHAR(36) NOT NULL, payload_hash VARCHAR(64) NOT NULL,
 created_at TIMESTAMP NOT NULL, PRIMARY KEY(user_id,request_id)
);
CREATE TABLE IF NOT EXISTS post_category (
 post_id INT PRIMARY KEY, category VARCHAR(30) NOT NULL
);
CREATE TABLE IF NOT EXISTS post_image_binary (
 image_id INT PRIMARY KEY, mime_type VARCHAR(30) NOT NULL, image_data MEDIUMBLOB NOT NULL
);
CREATE TABLE IF NOT EXISTS comment_reply_target (
 comment_id INT PRIMARY KEY, reply_to_id INT NOT NULL
);
