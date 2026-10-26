package com.commerce.payment;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.ObjectMapper;

@Component
public class KafkaTransport {
    private final ObjectMapper json;
    private final PaymentService payments;
    public KafkaTransport(ObjectMapper json, PaymentService payments) {
        this.json=json; this.payments=payments;
    }
    @KafkaListener(topics="${commerce.topic}")
    public void consume(ConsumerRecord<String,String> record) {
        var event = SagaEvent.parse(json,record.value());
        if (!event.path("aggregateId").asText().equals(record.key())) throw new IllegalArgumentException("Kafka key mismatch");
        payments.accept(event);
    }
    @Configuration
    static class FailureHandling {
        @Bean
        DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String,String> template) {
            var recoverer = new DeadLetterPublishingRecoverer(template,
                    (record,error)->new org.apache.kafka.common.TopicPartition(record.topic()+".payment-service.DLT",record.partition()));
            recoverer.setFailIfSendResultIsError(true);
            var handler = new DefaultErrorHandler(recoverer,new FixedBackOff(1000,3));
            handler.addNotRetryableExceptions(IllegalArgumentException.class);
            return handler;
        }
    }
}

