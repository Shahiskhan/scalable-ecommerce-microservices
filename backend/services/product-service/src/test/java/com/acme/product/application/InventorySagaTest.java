package com.acme.product.application;

import com.acme.product.messaging.Outbox;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventorySagaTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private InventorySaga saga;
    private final ObjectMapper json = JsonMapper.builder().build();
    private final String orderId = UUID.randomUUID().toString();
    @BeforeEach void setup() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__catalog_inventory_and_delivery.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var outbox = new Outbox(jdbc, json, mock(KafkaTemplate.class), "commerce.saga.v1");
        saga = new InventorySaga(jdbc, json, outbox, mock(CatalogService.class));
        jdbc.update("INSERT INTO product(sku,name,price_minor,currency,available_stock) VALUES('A','A',299,'USD',3)");
    }
    private String event(String type, Map<String, Object> fields) {
        var payload = new java.util.HashMap<String, Object>(fields);
        payload.put("orderId", orderId); payload.put("userId", "buyer");
        return json.writeValueAsString(Map.of("eventId", UUID.randomUUID().toString(), "eventType", type, "schemaVersion", 1,
                "aggregateId", orderId, "occurredAt", Instant.now().toString(), "payload", payload));
    }
    private String placed(int quantity) {
        return event("OrderPlaced", Map.of("expiresAt", Instant.now().plusSeconds(300).toString(), "items", List.of(Map.of("sku", "A", "quantity", quantity))));
    }
    private void accept(String event) { tx.executeWithoutResult(status -> saga.accept(orderId, event)); }
    private int stock() { return jdbc.queryForObject("SELECT available_stock FROM product WHERE sku='A'", Integer.class); }
    private String status() { return jdbc.queryForObject("SELECT status FROM inventory_reservation WHERE order_id=?", String.class, orderId); }
    @Test void duplicateTransportAndBusinessEventsReserveOnceAndPublishOnce() {
        String event = placed(2);
        accept(event); accept(event); accept(placed(2));
        assertEquals(1, stock());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox", Integer.class));
        assertEquals("RESERVED", status());
        var payload = json.readTree(jdbc.queryForObject("SELECT payload FROM outbox", String.class)).path("payload");
        assertEquals(598, payload.path("totalMinor").longValue());
    }
    @Test void failedPaymentReleasesOnceDespiteDuplicateCompensation() {
        accept(placed(2));
        accept(event("PaymentFailed", Map.of("reason", "DECLINED")));
        accept(event("OrderCancelled", Map.of("reason", "PAYMENT_FAILED")));
        assertEquals(3, stock());
        assertEquals("RELEASED", status());
    }
    @Test void latePlacementCannotResurrectCancelledOrder() {
        accept(event("OrderCancelled", Map.of("reason", "CANCELLED")));
        accept(placed(2));
        assertEquals(3, stock());
        assertEquals("RELEASED", status());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM outbox", Integer.class));
    }
    @Test void rejectionPreservesStockAndProducesTerminalResult() {
        accept(placed(4));
        assertEquals(3, stock());
        assertEquals("REJECTED", status());
        assertEquals("InventoryRejected", json.readTree(jdbc.queryForObject("SELECT payload FROM outbox", String.class)).path("eventType").stringValue());
    }
    @Test void confirmationCommitsAndLateFailureCannotIncreaseStock() {
        accept(placed(2));
        accept(event("OrderConfirmed", Map.of()));
        accept(event("PaymentFailed", Map.of("reason", "LATE")));
        assertEquals(1, stock());
        assertEquals("COMMITTED", status());
    }
    @Test void domainAndInboxRollbackTogether() {
        String placed = placed(2);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> { saga.accept(orderId, placed); throw new IllegalStateException("simulated crash"); }));
        assertEquals(3, stock());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inbox", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM outbox", Integer.class));
        accept(placed);
        assertEquals(1, stock());
    }
    @Test void concurrentOrdersCannotOversellLastUnits() throws Exception {
        String otherOrder = UUID.randomUUID().toString();
        String first = placed(2), second = placed(2).replace(orderId, otherOrder);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var a = workers.submit(() -> { start.await(); accept(first); return null; });
            var b = workers.submit(() -> { start.await(); tx.executeWithoutResult(status -> saga.accept(otherOrder, second)); return null; });
            start.countDown();
            a.get(10, java.util.concurrent.TimeUnit.SECONDS); b.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(1, stock());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservation WHERE status='RESERVED'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservation WHERE status='REJECTED'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM outbox", Integer.class));
    }
}
