package com.acme.commerce.notification.infrastructure;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
@Configuration
public class KafkaConfiguration {
    @Bean DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String,String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template, (record, failure) -> new TopicPartition("commerce.saga.v1.notification-service.DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 4));
        handler.setResetStateOnRecoveryFailure(true);
        return handler;
    }
}
