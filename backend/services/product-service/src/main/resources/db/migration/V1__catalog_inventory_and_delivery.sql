CREATE TABLE product (
    sku VARCHAR(64) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    price_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    available_stock INT NOT NULL,
    CONSTRAINT ck_product_price CHECK (price_minor > 0),
    CONSTRAINT ck_product_stock CHECK (available_stock >= 0)
);
CREATE TABLE inventory_reservation (
    order_id CHAR(36) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    total_minor BIGINT,
    currency CHAR(3),
    expires_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE TABLE reservation_item (
    order_id CHAR(36) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    quantity INT NOT NULL,
    unit_price_minor BIGINT NOT NULL,
    PRIMARY KEY(order_id, sku),
    CONSTRAINT fk_reservation_item_order FOREIGN KEY(order_id) REFERENCES inventory_reservation(order_id),
    CONSTRAINT fk_reservation_item_product FOREIGN KEY(sku) REFERENCES product(sku),
    CONSTRAINT ck_reservation_quantity CHECK (quantity > 0)
);
CREATE TABLE inbox (
    event_id CHAR(36) PRIMARY KEY,
    received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE TABLE outbox (
    event_id CHAR(36) PRIMARY KEY,
    aggregate_id CHAR(36) NOT NULL,
    payload LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at TIMESTAMP(6)
);
CREATE INDEX ix_outbox_pending ON outbox(published_at, created_at);
