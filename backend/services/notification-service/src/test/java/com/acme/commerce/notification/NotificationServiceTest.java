package com.acme.commerce.notification;
import com.acme.commerce.notification.application.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class NotificationServiceTest {
    private NotificationService notifications;
    private JdbcTemplate jdbc;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:notifications;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("DROP TABLE IF EXISTS notifications"); jdbc.execute("DROP TABLE IF EXISTS inbox");
        jdbc.execute("CREATE TABLE inbox(event_id VARCHAR(36) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE notifications(id VARCHAR(36) PRIMARY KEY,order_id VARCHAR(36) UNIQUE,user_id VARCHAR(128),kind VARCHAR(32),message VARCHAR(200),created_at TIMESTAMP)");
        notifications=new NotificationService(jdbc, JsonMapper.builder().build());
    }
    private String event(String eventId,String orderId,String userId) {
        return "{\"eventId\":\""+eventId+"\",\"eventType\":\"OrderConfirmed\",\"schemaVersion\":1,\"aggregateId\":\""+orderId+"\",\"occurredAt\":\"2026-10-26T00:00:00Z\",\"payload\":{\"orderId\":\""+orderId+"\",\"userId\":\""+userId+"\"}}";
    }
    @Test void duplicatesProduceOneUserNotification() {
        String order=UUID.randomUUID().toString(), id=UUID.randomUUID().toString();
        notifications.consume(order,event(id,order,"alice"));
        notifications.consume(order,event(id,order,"alice"));
        notifications.consume(order,event(UUID.randomUUID().toString(),order,"alice"));
        assertThat(notifications.list("alice")).hasSize(1);
        assertThat(notifications.list("bob")).isEmpty();
    }
    @Test void corruptKeyCannotAcknowledgeOrCreateNotification() {
        String order=UUID.randomUUID().toString();
        assertThatThrownBy(()->notifications.consume("wrong",event(UUID.randomUUID().toString(),order,"alice"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inbox",Long.class)).isZero();
        assertThat(notifications.list("alice")).isEmpty();
    }
}
