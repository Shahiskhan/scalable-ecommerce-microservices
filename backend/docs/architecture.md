# Architecture decisions

## Domain ownership and isolation

User owns business profile data; Firebase owns login credentials and roles. Product owns SKU identity, current price, stock and reservations. Cart is a subordinate Product capability initially: this avoids a sixth service before independent ownership or scale requires it, while the Redis keyspace and HTTP contract allow extraction. Order owns the customer's immutable submitted items and checkout state. Payment owns payment decisions and the provider boundary. Notification owns durable in-app messages. Each uses a separate MySQL instance and distinct database credentials, with Compose network isolation for every database.

No service calls another service's repository. No ORM relationship crosses a service boundary. Integrate by versioned events or explicit HTTP contracts. Local source duplication for tiny infrastructure adapters is intentional; a shared domain library would couple release cycles.

## Edge trust model

The gateway is the only Firebase JWT verifier. It validates the RS256 signature and key ID against Google's keys, exact project audience and issuer, nonempty UID, expiry, issue time and authentication time. An authenticated Firebase custom role claim controls catalog writes. JWT verification does not perform Firebase revocation lookups; account disablement or role removal is reflected when tokens expire/refresh. A stricter revocation policy needs a gateway-side revocation design with an explicit availability tradeoff.

Client X-Internal-* headers are stripped. The gateway creates user, sorted roles, epoch timestamp and a fresh UUID request ID, signs them using HMAC-SHA256, and removes the Authorization token downstream. All exposed domain APIs verify that signature and a 30-second clock window before enforcing ownership or role rules. The canonical signing text is method, raw URI path without query, UID, roles, timestamp and request UUID joined with newline characters. Signature bytes use URL-safe Base64 without padding. Internal headers are never accepted solely because a request came from a private IP.

Internal TLS and network policy remain mandatory in production: the context signature is not encryption and does not bind query parameters or body bytes. The same signed request can be replayed inside its short validity window by someone who obtains it. State-changing business APIs must keep their own idempotency/concurrency controls. Shared HMAC key possession is a trust boundary: production key rotation and workload identities require deployment work. Product/order ownership cannot be delegated by changing request JSON.

## Order choreography

1. Order validates at most 20 unique uppercase SKUs, quantities 1..1000, and a customer-scoped idempotency key. In one MySQL transaction it writes PENDING, items, and an OrderPlaced outbox event. Client prices are never accepted.
2. Product consumes OrderPlaced, records the event ID in its inbox and locks SKU rows in sorted order. It reserves all items or none. A successful transaction writes the reservation, decrements available stock, and enqueues InventoryReserved with an authoritative integer USD amount. Definitive rejection enqueues InventoryRejected without changing stock.
3. Order records the reserved amount and transitions to RESERVED. Payment independently consumes InventoryReserved and records either PaymentAuthorized or PaymentFailed through its own outbox. Expired reservations are declined before a new sandbox authorization.
4. Order accepts matching payment authorization only for its reserved amount and owner, then writes CONFIRMED and OrderConfirmed atomically. Definitive inventory/payment rejection writes CANCELLED and OrderCancelled.
5. Product commits reservations on OrderConfirmed or releases them on PaymentFailed / OrderCancelled. Release is idempotent. Terminal tombstones prevent delayed placement events from resurrecting a released reservation. Notification persists one user-scoped message per terminal order.

All events use commerce.saga.v1, keyed by order UUID, with schemaVersion 1 and the envelope in contracts/. Groups are independent per service; same-order events share a partition. A single saga topic is an initial contract boundary, not a claim of unlimited capacity. New bounded contexts may warrant separate topics with explicit sequencing guarantees and per-producer ACLs. Only trusted workloads may publish; the current common topic does not cryptographically authenticate producer identity.

## Delivery guarantees and failure handling

A local transaction couples domain mutations, inbox deduplication and outbox insertion. Outbox workers publish with broker acknowledgement before marking rows sent. A crash after broker acceptance and before database commit may publish the same event again; consumers deduplicate within their own domain transaction. Kafka producer idempotence improves producer retries, but it does not turn a MySQL plus Kafka workflow into a single distributed transaction.

Bounded listener retries lead to a service-specific dead-letter topic with the same partition count. A DLT publish must succeed before recovery can advance the source offset. A quarantined event blocks that workflow's eventual progress until repaired/replayed; DLT is an operator-visible state requiring alerts, not successful order completion. Unsent outbox rows remain durable and are retried on subsequent polls.

There is no speculative automatic inventory release after a reservation deadline: releasing while a payment's outcome is unknown can oversell paid stock. This foundation deliberately retains unresolved reservations for reconciliation. Before enabling a real provider, implement durable payment attempts, idempotent provider requests, timeout reconciliation and refunds/voids for late success, plus a stale-saga sweeper driven by verified payment facts. A timeout alone is not a definitive decline.

Gateway rate limits return503 if Redis cannot supply a valid quota decision; a healthy exhausted quota returns429. HTTP GET routes have bounded retries and circuit breakers. Mutating routes do not receive gateway retries; clients retry checkout using the same idempotency key. Pool limits and short connection deadlines constrain synchronous resource consumption. Catalog cache failures fall back to the authoritative database; cart failures surface an error rather than fabricating a successful write. Kafka listener concurrency is bounded by partitions, and MySQL locking protects stock across service replicas.

## Cache and cart semantics

Catalog reads use a short TTL. Invalidation happens after database commit; invalidation failures can expose stale catalog display data until TTL expiry. Checkout always uses locked MySQL price/stock, never the cache. Carts use namespaced Redis keys and a 30-day TTL, store quantities only, and are replaced as a whole. Concurrent cart replacements use last-writer-wins semantics. Redis AOF limits local restart loss; Redis replication/failover does not guarantee zero cart loss. Inventory and committed orders are never stored solely in Redis.

## Deployment and scale

Compose is one-host development topology with single broker/registry/config replicas. It cannot survive host loss. Scale stateless gateway/services independently only after measuring their bottlenecks. Kafka partitions bound consumer throughput, hot SKUs serialize on inventory locks, outbox polling adds latency, and database connections must be budgeted across replicas. Do not increase pools or replicas without checking the downstream budget. The database remains authoritative for business invariants.

High availability is a deployment property requiring multiple fault domains, backups, tested failover and operational ownership. The initial architecture favors explicit consistency guarantees and repairable state over undocumented distributed assumptions. Operational rollout gates are in operations.md.
