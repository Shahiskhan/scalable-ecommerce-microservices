package com.acme.commerce.notification.application;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public NotificationService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    @Transactional
    public void consume(String key, String body) {
        JsonNode event = json.readTree(body);
        String type = event.path("eventType").asText();
        if (!List.of("OrderConfirmed", "OrderCancelled").contains(type)) return;
        if (event.path("schemaVersion").asInt() != 1) throw new IllegalArgumentException("Unsupported event version");
        String eventId = event.path("eventId").asText();
        UUID.fromString(eventId);
        String orderId = event.path("aggregateId").asText();
        UUID.fromString(orderId);
        if (!orderId.equals(key) || !orderId.equals(event.path("payload").path("orderId").asText())) throw new IllegalArgumentException("Invalid event key");
        Instant.parse(event.path("occurredAt").asText());
        String userId = event.path("payload").path("userId").asText();
        if (userId.isBlank() || userId.length() > 128) throw new IllegalArgumentException("Invalid user ID");
        try { jdbc.update("INSERT INTO inbox(event_id) VALUES (?)", eventId); }
        catch (DuplicateKeyException duplicate) { return; }
        // Domain uniqueness handles a producer publishing the same fact with a new event ID.
        if (jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE order_id=?", Long.class, orderId) != 0) return;
        jdbc.update("INSERT INTO notifications(id,order_id,user_id,kind,message,created_at) VALUES (?,?,?,?,?,?)",
            eventId, orderId, userId, type, "OrderConfirmed".equals(type) ? "Your order is confirmed" : "Your order was cancelled", java.sql.Timestamp.from(Instant.now()));
    }
    public List<Notice> list(String userId) {
        return jdbc.query("SELECT id,order_id,kind,message,created_at FROM notifications WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 50",
            (rs,n) -> new Notice(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toInstant()), userId);
    }
    public record Notice(String id, String orderId, String kind, String message, Instant createdAt) { }
}
