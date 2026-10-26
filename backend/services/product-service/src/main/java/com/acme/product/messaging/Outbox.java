package com.acme.product.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Acknowledgement precedes marking. Crash gaps cause duplicates, handled by every consumer's inbox. */
@Component
public class Outbox {
    private static final Logger log = LoggerFactory.getLogger(Outbox.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    public Outbox(JdbcTemplate jdbc, ObjectMapper json, KafkaTemplate<String, String> kafka, @Value("${commerce.saga-topic}") String topic) {
        this.jdbc = jdbc; this.json = json; this.kafka = kafka; this.topic = topic;
    }
    public void append(String type, String orderId, Map<String, Object> payload) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Outbox requires a domain transaction");
        String eventId = UUID.randomUUID().toString();
        String envelope = json.writeValueAsString(Map.of("eventId", eventId, "eventType", type, "schemaVersion", 1,
                "aggregateId", orderId, "occurredAt", Instant.now().toString(), "payload", payload));
        jdbc.update("INSERT INTO outbox(event_id, aggregate_id, payload) VALUES(?,?,?)", eventId, orderId, envelope);
    }
    private record Pending(String eventId, String aggregateId, String payload) {}
    @Scheduled(fixedDelayString="${commerce.outbox-delay-ms:500}")
    @Transactional(timeout=25)
    public void publishOne() {
        var pending = jdbc.query("SELECT event_id,aggregate_id,payload FROM outbox WHERE published_at IS NULL ORDER BY created_at,event_id LIMIT 1 FOR UPDATE SKIP LOCKED",
                (rs, row) -> new Pending(rs.getString(1), rs.getString(2), rs.getString(3)));
        if (pending.isEmpty()) return;
        var event = pending.getFirst();
        try {
            kafka.send(topic, event.aggregateId(), event.payload()).get(20, TimeUnit.SECONDS);
            jdbc.update("UPDATE outbox SET published_at=? WHERE event_id=?", Timestamp.from(Instant.now()), event.eventId());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("outbox_publish_interrupted eventId={}", event.eventId());
        } catch (Exception unavailable) {
            log.warn("outbox_publish_pending eventId={} errorType={}", event.eventId(), unavailable.getClass().getSimpleName());
        }
    }
}
