# Product service

Independent Java 25/Spring Boot service owning its MySQL catalog, inventory, reservation ledger, inbox and outbox. This bounded context also owns cart intent in Redis; extract a separate cart service when its ownership or traffic needs justify that operational cost.

Build from this directory with `mvn verify`. The service does not depend on another project's classes or parent POM. Its Docker build runs the tests. Required runtime variables are `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CONFIG_SERVER_URL`, `CONFIG_USERNAME`, `CONFIG_PASSWORD`, `EUREKA_URL`, `INTERNAL_HMAC_SECRET`, `KAFKA_BOOTSTRAP_SERVERS`, `REDIS_HOST`, `REDIS_PORT`, and `REDIS_PASSWORD`. Activate the additional `redis-cluster` Spring profile and provide comma-separated `REDIS_CLUSTER_NODES` for cluster mode. Keep TLS and credentials in deployment configuration; local Compose is a development environment.

## API

All `/api/**` requests require a gateway-signed context. The gateway validates Firebase JWTs; this service verifies internal authenticity and enforces ownership/administrator authorization. Internal HMAC is not encryption, does not sign request bodies, and has a 30-second freshness window, so authenticated internal TLS and network isolation are required in production. Never expose this service directly to the Internet.

- `GET /api/products?after=SKU-1&size=20` returns a bounded catalog page using indexed keyset pagination. Omit `after` for the first page; pass the last returned SKU for the next page.
- `GET /api/products/{sku}` returns the product or 404.
- `POST /api/products` requires `ADMIN`. Body: `{"sku":"SKU-1","name":"Example","priceMinor":299,"currency":"USD","stock":20}`. SKU and catalog price are immutable in this initial API.
- `GET /api/cart` and `PUT /api/cart` operate only on the signed user's cart. Replace body: `{"items":[{"sku":"SKU-1","quantity":2}]}`. Maximum 20 unique SKUs, quantities 1–1000, 30-day Redis TTL. An empty `items` array clears cart intent. Cart is not a price quote or stock reservation.

Catalog cache failure falls back to MySQL. Cached product reads can lag inventory by up to the 30-second TTL, including concurrent cache-fill races. Reservation always reads current prices and locks current stock in MySQL. Cart Redis failure returns 503; the API never presents a storage failure as an empty cart.

## Saga and delivery guarantees

Consumes `commerce.saga.v1` as group `product-service-v1`. Every event is keyed by order UUID and uses schema version 1. `OrderPlaced` contains `orderId`, `userId`, `items`, and `expiresAt`. Inventory rows are locked in sorted SKU order, the full basket is checked before any decrement, and authoritative USD prices are captured in reservation lines. The service writes either `InventoryReserved` with `totalMinor`/`currency`/`expiresAt` or `InventoryRejected` with a stable reason. Inventory, inbox and outbox commit in one local transaction.

`PaymentFailed` and `OrderCancelled` release reserved stock once. `OrderConfirmed` commits once. Terminal records prevent delayed placement events from reopening a saga. Compensation after a committed sale is a distinct refund/return workflow and is deliberately not inferred from a contradictory late failure event. No automatic reservation expiry releases stock while a payment might be in flight; operators must reconcile stuck sagas against payment outcomes.

Outbox publisher locks one pending row using MySQL `FOR UPDATE SKIP LOCKED`, waits for Kafka acknowledgement, then marks it published. A crash between Kafka acknowledgement and database commit can republish; consumers must keep durable inboxes. Delivery is at least once, with idempotent local effects. Domain events remain pending while Kafka is unavailable. Repeated consumer failures are routed to `commerce.saga.v1.product-service.DLT`, which must have the same partition count as the source. DLT publishing failure prevents successful recovery. Alert and investigate this topic; do not blindly replay malformed or contradictory events. Inbox/reservation tombstone retention must exceed source-topic retention and the allowed operational replay horizon.

## Verification and production work

Tests cover authoritative money calculation, expiry, overflow, duplicate events, compensation, terminal ordering, rollback of inbox/domain/outbox, concurrent competing orders and signed-context spoofing. JDBC tests use H2 MySQL mode; production acceptance additionally needs real MySQL/Kafka/Redis integration tests, load testing, fault injection, broker ACLs, encrypted transport, database backups and tested restoration, inventory replenishment/returns APIs and saga reconciliation tooling.
