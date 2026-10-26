CREATE TABLE orders (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_minor BIGINT,
    currency VARCHAR(3),
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    UNIQUE (user_id, idempotency_key)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE TABLE order_items (
    order_id VARCHAR(36) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (order_id, sku),
    FOREIGN KEY (order_id) REFERENCES orders(id)
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

