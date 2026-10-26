CREATE TABLE inbox (event_id VARCHAR(36) PRIMARY KEY, received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6));
CREATE TABLE notifications (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL UNIQUE,
    user_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    kind VARCHAR(32) NOT NULL,
    message VARCHAR(200) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX notifications_user_created ON notifications(user_id,created_at);
