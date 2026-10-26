# Operations and production rollout

## Local topology

Compose publishes only gateway on loopback port 8080. Five MySQL instances and two Redis instances persist to named volumes. Product cache/cart Redis is separated from edge rate-limit Redis. Kafka runs one KRaft broker/controller and creates a 12-partition saga topic plus a matching DLT per consumer. Local database and broker traffic is plaintext inside isolated Docker networks. All JVM images run as UID10001 with read-only root filesystems, bounded memory and writable temporary directories. Credentials are required environment inputs and local .env is ignored.

Changing a MySQL environment password after its volume is initialized does not change the stored user password; rotate it with a database migration/admin procedure and then update the service secret. Do not delete persistent volumes to fix credentials unless you intentionally want to discard local data. docker compose down stops the stack while retaining data; adding --volumes destroys local persistent data.

## Before serving real customers

- Supply a real payment provider adapter and durable request/response reconciliation. Provider calls need deadlines, circuit breakers, provider-supported idempotency, and a worker lease rather than a long open DB transaction. Implement webhook authenticity, capture/void/refund semantics and recovery for late success. Payment intentionally refuses a production start today.
- Run MySQL with per-service HA and tested encrypted backups / point-in-time recovery. Use separate migration credentials rather than application DDL privileges, per-service accounts, TLS VERIFY_IDENTITY and connection budgets.
- Run Kafka across at least three fault domains with replication factor3, min.insync.replicas2, acks=all, bounded retention and capacity monitoring. Configure TLS/SASL and least-privilege consumer-group/topic ACLs. Disable automatic topic creation. Replicate DLT durability as carefully as source events.
- Use a production Redis Cluster (three primaries with replicas across fault domains), authentication/ACLs, TLS and tested persistence/failover. Activate each service's redis-cluster profile and set REDIS_CLUSTER_NODES; confirm cluster slot/endpoint reachability from workloads. Understand AOF/replication loss windows and cart recovery requirements.
- Replicate Config Server and Eureka, configure registry peers, private authenticated HTTPS, and a versioned configuration source. Deploy multiple gateway/domain replicas behind a TLS ingress. Enforce mTLS/network policies between gateway and services. Restrict service-to-database/broker egress. No database or internal actuator port should be public.
- Put secrets in a secret manager with rotation. Reject .env-style plaintext secrets in deployment artifacts. Mount trust stores/keystores without building them into images. Validate Firebase project separation and admin-claim lifecycle.
- Publish OCI images through CI, pin image digests, scan dependencies/images, produce an SBOM, and retain provenance. Current development image tags are not a software supply-chain approval.
- Add container integration tests using real MySQL/Kafka/Redis; test lock contention, broker loss, duplicate delivery, consumer crashes between commit/offset ack, database failover and DLT replay. Use load tests to set concrete latency/throughput/error SLOs rather than claiming hyper-scale from architecture alone.

## Configuration

Domain clients fail startup if Config Server cannot be reached. Local config-repo/application.yml contains non-secret common defaults. Config Server's prod profile selects a Git backend using CONFIG_GIT_URI and CONFIG_GIT_LABEL. Set CONFIG_SERVER_URL and EUREKA_URL to private HTTPS endpoints with credentials from the secret manager. Production TLS/broker/Redis settings belong in the versioned external config, with sensitive values referenced from secret inputs. Application env variables can override Spring config where incident recovery requires it.

No production infrastructure is provisioned in this repository. A cloud/provider choice, RPO/RTO, region/fault-domain plan and realistic traffic forecast are needed before writing a useful deployment manifest. Simply deploying local Compose to a VM would retain all of its single points of failure.

## Recovery runbook

For an order stuck in PENDING or RESERVED, trace the order UUID across the service-owned state tables, source topic and consumer-specific DLT. Use each service's admin/migration connection in an authorized operations environment; do not add cross-service SQL queries to application code. Check outbox backlog and consumer lag before assuming a customer payment failed. Preserve the idempotency key when a client retries.

If a listener retries transient database/broker errors, allow recovery and monitor backlog. If a record reaches a DLT, investigate its exception metadata and original key. Fix the incompatibility or bad operational condition; validate the payload against its contract, then replay to the original topic with the original eventId and aggregate key. Do not generate a fresh event ID to bypass an inbox row. Do not replay an entire DLT indiscriminately, and do not manually mark an unresolved payment as declined.

Current deadlines prevent initiating expired work; a stale-saga sweeper and payment reconciliation worker are production rollout gates. No task currently deletes outbox/inbox history. Add indexed retention/archival only after defining a replay horizon. Keep inbox dedupe state at least as long as possible event replay; deleting it earlier can repeat side effects. Retain idempotency keys for an explicitly documented customer retry horizon.

## Observability

Logs use structured JSON and signed request IDs where applicable; never log tokens, signature secrets or payment credentials. Exposed actuator surface is health only. Configure liveness to process health and readiness to dependencies through private management endpoints. Prometheus registry dependencies are present, but authenticated private scraping, distributed tracing exporters, dashboards and alert delivery require deployment configuration.

Alert on oldest unsent outbox age/count, DLT arrivals, consumer lag, stale orders/reservations, database lock waits and pool saturation, Redis failures/evictions, rate-limit failures, gateway 5xx/circuit state and certificate expiry. Instrument those business metrics before rollout; a dependency's presence alone does not create the dashboards or alerts. Test backup restoration and a full order recovery drill regularly.
