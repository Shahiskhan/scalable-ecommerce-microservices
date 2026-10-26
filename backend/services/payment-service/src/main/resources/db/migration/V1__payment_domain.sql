CREATE TABLE payments (
    order_id VARCHAR(36) PRIMARY KEY,
    payment_id VARCHAR(64) NOT NULL UNIQUE,
    user_id VARCHAR(128) NOT NULL,
    total_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE TABLE inbox (event_id VARCHAR(36) PRIMARY KEY, received_at TIMESTAMP(6) NOT NULL) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE TABLE outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    aggregate_id VARCHAR(36) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6),
    INDEX outbox_pending (published_at, id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

