package com.acme.product.messaging;

import com.acme.product.application.InventorySaga;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

@Component
public class KafkaDelivery {
    private final InventorySaga saga;
    public KafkaDelivery(InventorySaga saga) { this.saga = saga; }
    @KafkaListener(topics="${commerce.saga-topic}")
    public void receive(@Header(KafkaHeaders.RECEIVED_KEY) String key, String data) { saga.accept(key, data); }
    @Configuration
    static class Recovery {
        @Bean
        DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
            var recoverer = new DeadLetterPublishingRecoverer(kafka, (record, error) -> new TopicPartition(record.topic() + ".product-service.DLT", record.partition()));
            recoverer.setFailIfSendResultIsError(true);
            var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 4));
            handler.addNotRetryableExceptions(IllegalArgumentException.class);
            return handler;
        }
    }
}
