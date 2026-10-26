package com.acme.product.application;

import com.acme.product.domain.InventoryPlan;
import com.acme.product.domain.Product;
import com.acme.product.messaging.Outbox;
import com.acme.product.messaging.SagaEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class InventorySaga {
    private static final Set<String> SUPPORTED = Set.of("OrderPlaced", "PaymentFailed", "OrderCancelled", "OrderConfirmed");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Outbox outbox;
    private final CatalogService catalog;
    public InventorySaga(JdbcTemplate jdbc, ObjectMapper json, Outbox outbox, CatalogService catalog) { this.jdbc = jdbc; this.json = json; this.outbox = outbox; this.catalog = catalog; }
    @Transactional(timeout=15)
    public void accept(String key, String data) {
        SagaEvent event = SagaEvent.read(json, key, data);
        if (!SUPPORTED.contains(event.eventType())) return;
        if (!jdbc.queryForList("SELECT event_id FROM inbox WHERE event_id=?", String.class, event.eventId()).isEmpty()) return;
        jdbc.update("INSERT INTO inbox(event_id) VALUES(?)", event.eventId());
        if (event.eventType().equals("OrderPlaced")) reserve(event);
        else settle(event, event.eventType().equals("OrderConfirmed"));
    }
    private record Reservation(String userId, String status) {}
    private List<Reservation> lock(String orderId) {
        return jdbc.query("SELECT user_id,status FROM inventory_reservation WHERE order_id=? FOR UPDATE", (rs, row) -> new Reservation(rs.getString(1), rs.getString(2)), orderId);
    }
    private void reserve(SagaEvent event) {
        String orderId = event.aggregateId(), userId = SagaEvent.text(event.payload(), "userId");
        var existing = lock(orderId);
        if (!existing.isEmpty()) { checkUser(existing.getFirst(), userId); return; }
        Instant expires = Instant.parse(SagaEvent.text(event.payload(), "expiresAt"));
        jdbc.update("INSERT INTO inventory_reservation(order_id,user_id,status,expires_at) VALUES(?,?,'PENDING',?)", orderId, userId, Timestamp.from(expires));
        List<InventoryPlan.Item> items = items(event);
        Map<String, Product> products = new HashMap<>();
        // Stable lock order bounds deadlocks for simultaneous overlapping baskets.
        for (String sku : items.stream().map(InventoryPlan.Item::sku).distinct().sorted().toList()) {
            var found = jdbc.query("SELECT * FROM product WHERE sku=? FOR UPDATE", CatalogService.PRODUCT_MAPPER, sku);
            if (!found.isEmpty()) products.put(sku, found.getFirst());
        }
        InventoryPlan plan;
        try { plan = InventoryPlan.prepare(items, products, expires, Instant.now()); }
        catch (InventoryPlan.Rejected rejection) {
            jdbc.update("UPDATE inventory_reservation SET status='REJECTED' WHERE order_id=?", orderId);
            outbox.append("InventoryRejected", orderId, Map.of("orderId", orderId, "userId", userId, "reason", rejection.getMessage()));
            return;
        }
        for (var line : plan.lines()) {
            int updated = jdbc.update("UPDATE product SET available_stock=available_stock-? WHERE sku=? AND available_stock>=?", line.quantity(), line.sku(), line.quantity());
            if (updated != 1) throw new IllegalStateException("Stock invariant violated");
            jdbc.update("INSERT INTO reservation_item(order_id,sku,quantity,unit_price_minor) VALUES(?,?,?,?)", orderId, line.sku(), line.quantity(), line.unitPriceMinor());
        }
        jdbc.update("UPDATE inventory_reservation SET status='RESERVED',total_minor=?,currency='USD' WHERE order_id=?", plan.totalMinor(), orderId);
        outbox.append("InventoryReserved", orderId, Map.of("orderId", orderId, "userId", userId, "totalMinor", plan.totalMinor(), "currency", "USD", "expiresAt", expires.toString()));
        catalog.evictAfterCommit(plan.lines().stream().map(InventoryPlan.Line::sku).toList());
    }
    private List<InventoryPlan.Item> items(SagaEvent event) {
        var node = event.payload().path("items");
        if (!node.isArray() || node.size() > 20) throw new IllegalArgumentException("Invalid order items");
        List<InventoryPlan.Item> items = new ArrayList<>();
        for (var item : node) {
            String sku = SagaEvent.text(item, "sku");
            if (!sku.matches("[A-Z0-9_-]{1,64}") || !item.path("quantity").isIntegralNumber() || !item.path("quantity").canConvertToInt()) throw new IllegalArgumentException("Invalid order item");
            items.add(new InventoryPlan.Item(sku, item.path("quantity").intValue()));
        }
        return items;
    }
    private record ReservedItem(String sku, int quantity) {}
    private void settle(SagaEvent event, boolean commit) {
        String orderId = event.aggregateId(), userId = SagaEvent.text(event.payload(), "userId");
        var existing = lock(orderId);
        String terminal = commit ? "COMMITTED" : "RELEASED";
        if (existing.isEmpty()) {
            // Tombstone prevents a delayed OrderPlaced from resurrecting a terminated saga.
            jdbc.update("INSERT INTO inventory_reservation(order_id,user_id,status) VALUES(?,?,?)", orderId, userId, terminal);
            return;
        }
        var reservation = existing.getFirst();
        checkUser(reservation, userId);
        if (!reservation.status().equals("RESERVED")) return;
        if (!commit) {
            var lines = jdbc.query("SELECT sku,quantity FROM reservation_item WHERE order_id=? ORDER BY sku", (rs, row) -> new ReservedItem(rs.getString(1), rs.getInt(2)), orderId);
            for (var line : lines) {
                jdbc.queryForObject("SELECT available_stock FROM product WHERE sku=? FOR UPDATE", Integer.class, line.sku());
                jdbc.update("UPDATE product SET available_stock=available_stock+? WHERE sku=?", line.quantity(), line.sku());
            }
            catalog.evictAfterCommit(lines.stream().map(ReservedItem::sku).toList());
        }
        jdbc.update("UPDATE inventory_reservation SET status=? WHERE order_id=?", terminal, orderId);
    }
    private static void checkUser(Reservation reservation, String userId) {
        if (!reservation.userId().equals(userId)) throw new IllegalArgumentException("Saga identity mismatch");
    }
}
