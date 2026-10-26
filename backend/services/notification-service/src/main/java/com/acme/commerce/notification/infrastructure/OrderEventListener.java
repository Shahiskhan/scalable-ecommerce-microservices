package com.acme.commerce.notification.infrastructure;
import com.acme.commerce.notification.application.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
@Component
public class OrderEventListener {
    private final NotificationService notifications;
    public OrderEventListener(NotificationService notifications) { this.notifications = notifications; }
    @KafkaListener(topics="commerce.saga.v1")
    public void listen(String value, @Header(KafkaHeaders.RECEIVED_KEY) String key) { notifications.consume(key,value); }
}
