# API gateway

Build independently with Java 25: `mvn verify`. Port: 8080. This service validates Firebase **ID tokens**, not Firebase custom tokens. Supply `FIREBASE_PROJECT_ID`, `INTERNAL_HMAC_SECRET` (at least 32 UTF-8 bytes), `CONFIG_SERVER_URL`, `CONFIG_USERNAME`, `CONFIG_PASSWORD`, `EUREKA_URL` (authenticated registry URL), and Redis connection variables. Startup requires configuration and discovery; there is no unauthenticated bypass profile.

Explicit public API routes are users, products, cart, orders, and notifications. All require a valid Firebase token; catalog writes require an `admin: true` or `roles: ["ADMIN"]` Firebase custom claim managed by a privileged server. No payment endpoints or discovery-based wildcard routes are exposed. Only health probes are anonymous. Browser CORS origins must be explicitly configured before adding a browser frontend.

The gateway removes inbound `X-Internal-*` and the bearer token and creates a signed internal identity context. Headers: `X-Internal-User-Id`, `X-Internal-Roles` (sorted comma separated), `X-Internal-Timestamp` (epoch seconds), `X-Internal-Request-Id` (UUID), `X-Internal-Signature` (base64url without padding HMAC-SHA256). Canonical UTF-8 bytes:

```
METHOD\nRAW_PATH_WITHOUT_QUERY\nUSER_ID\nSORTED_ROLES\nTIMESTAMP\nREQUEST_ID
```

Receivers validate with constant-time comparison and <=30 second clock skew, then enforce resource ownership and domain authorization locally. The signature authenticates identity context and does not bind body/query contents; internal TLS, restricted service ingress, and secret rotation remain required. No external bearer token is forwarded downstream. HMAC key sharing is an initial deployment tradeoff; production service-mesh mTLS and workload identities should restrict accepted gateway callers.

Redis rate limiting is per authenticated user (20 requests/second, burst 40), and Redis failure denies routing. Bound connection/response timeouts and circuit breakers prevent unbounded waiting. Retries are limited to one **GET** attempt; writes are never retried at the edge. Mutation idempotency is enforced in the domain service. Apply the `cluster` profile with `REDIS_CLUSTER_NODES` and `REDIS_TLS_ENABLED` for a managed Redis cluster. Local Compose uses its own Redis instance.

Offline JWT verification cannot immediately detect disabled/revoked users. Firebase tokens remain usable until expiry unless an explicit revocation cache/check is added for sensitive operations. RSA key fetching/cache is handled by Spring Security's Nimbus decoder; unknown keys or a missing initial key set fail authentication.
