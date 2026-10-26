package com.commerce.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;

@Service
public class OrderService {
    public record Item(@NotBlank @Pattern(regexp="[A-Z0-9_-]{1,64}") String sku,
                       @Min(1) @Max(1000) int quantity) { }
    public record PlaceOrder(@NotEmpty @Size(max=20) List<@NotNull @Valid Item> items) { }
    public record OrderView(String orderId, String status, Long totalMinor, String currency, List<Item> items) { }
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final Duration lifetime;

    public OrderService(JdbcTemplate db, TransactionTemplate tx, ObjectMapper json,
                        @Value("${commerce.order-lifetime:PT15M}") Duration lifetime) {
        this.db = db; this.tx = tx; this.json = json; this.lifetime = lifetime;
    }

    public OrderView place(String userId, String key, PlaceOrder request) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key is required");
        List<Item> items = request.items().stream().sorted(Comparator.comparing(Item::sku)).toList();
        if (items.stream().map(Item::sku).distinct().count() != items.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate SKUs are not permitted");
        String fingerprint = hash(items.stream().map(item -> item.sku()+":"+item.quantity())
                .collect(java.util.stream.Collectors.joining("\n")));
        return tx.execute(status -> {
            String candidate = UUID.randomUUID().toString();
            Instant now = Instant.now(), expiry = now.plus(lifetime);
            db.update("INSERT INTO orders(id,user_id,idempotency_key,request_hash,status,created_at,expires_at) " +
                    "VALUES(?,?,?,?,'PENDING',?,?) ON DUPLICATE KEY UPDATE id=id",
                    candidate, userId, key, fingerprint, Timestamp.from(now), Timestamp.from(expiry));
            Map<String,Object> row = db.queryForMap("SELECT * FROM orders WHERE user_id=? AND idempotency_key=? FOR UPDATE", userId, key);
            if (!fingerprint.equals(row.get("request_hash")))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was used for a different request");
            String id = row.get("id").toString();
            if (candidate.equals(id)) {
                items.forEach(item -> db.update("INSERT INTO order_items(order_id,sku,quantity) VALUES(?,?,?)", id, item.sku(), item.quantity()));
                enqueue(SagaEvent.create("OrderPlaced", id, Map.of("orderId",id,"userId",userId,"items",items,"expiresAt",expiry.toString())));
            }
            return readOwned(id, userId);
        });
    }

    public OrderView readOwned(String id, String userId) {
        List<OrderView> rows = db.query("SELECT id,status,total_minor,currency FROM orders WHERE id=? AND user_id=?",
                (rs, row) -> new OrderView(rs.getString(1),rs.getString(2),(Long)rs.getObject(3),rs.getString(4),List.of()),id,userId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        OrderView order = rows.getFirst();
        List<Item> items = db.query("SELECT sku,quantity FROM order_items WHERE order_id=? ORDER BY sku",
                (rs,row)->new Item(rs.getString(1),rs.getInt(2)), id);
        return new OrderView(order.orderId(),order.status(),order.totalMinor(),order.currency(),items);
    }

    public void accept(JsonNode event) {
        String type = event.path("eventType").asText();
        if (!Set.of("InventoryReserved","InventoryRejected","PaymentAuthorized","PaymentFailed").contains(type)) return;
        tx.executeWithoutResult(status -> {
            if (db.update("INSERT IGNORE INTO inbox(event_id,received_at) VALUES(?,?)",event.required("eventId").asText(),Timestamp.from(Instant.now())) == 0) return;
            String id = event.required("aggregateId").asText();
            JsonNode payload = event.required("payload");
            Map<String,Object> row = db.queryForMap("SELECT * FROM orders WHERE id=? FOR UPDATE",id);
            if (!row.get("user_id").equals(payload.path("userId").asText())) throw new IllegalArgumentException("Event owner mismatch");
            String current = row.get("status").toString();
            if (Set.of("CONFIRMED","CANCELLED").contains(current)) return;
            switch (type) {
                case "InventoryReserved" -> {
                    long total = amount(payload);
                    if (current.equals("PENDING")) db.update("UPDATE orders SET status='RESERVED',total_minor=?,currency=? WHERE id=?",total,"USD",id);
                    else if (((Number)row.get("total_minor")).longValue() != total) throw new IllegalArgumentException("Reservation amount mismatch");
                }
                case "PaymentAuthorized" -> {
                    long total = amount(payload);
                    if (!current.equals("RESERVED") || ((Number)row.get("total_minor")).longValue() != total)
                        throw new IllegalStateException("Payment does not match reserved order");
                    db.update("UPDATE orders SET status='CONFIRMED' WHERE id=?",id);
                    enqueue(SagaEvent.create("OrderConfirmed",id,Map.of("orderId",id,"userId",row.get("user_id"))));
                }
                case "InventoryRejected", "PaymentFailed" -> {
                    db.update("UPDATE orders SET status='CANCELLED' WHERE id=?",id);
                    enqueue(SagaEvent.create("OrderCancelled",id,Map.of("orderId",id,"userId",row.get("user_id"),"reason",type)));
                }
                default -> throw new IllegalArgumentException("Unsupported event");
            }
        });
    }

    private static long amount(JsonNode payload) {
        if (!payload.path("totalMinor").isIntegralNumber() || !payload.path("totalMinor").canConvertToLong()
                || payload.path("totalMinor").asLong() <= 0 || !"USD".equals(payload.path("currency").asText()))
            throw new IllegalArgumentException("Invalid authoritative money amount");
        return payload.path("totalMinor").asLong();
    }
    private void enqueue(SagaEvent event) {
        db.update("INSERT INTO outbox(event_id,aggregate_id,body,created_at) VALUES(?,?,?,?)",
                event.eventId(),event.aggregateId(),json.writeValueAsString(event),Timestamp.from(Instant.now()));
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
