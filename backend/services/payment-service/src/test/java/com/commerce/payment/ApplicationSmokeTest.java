package com.commerce.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("local")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
        "CONFIG_IMPORT=optional:classpath:/absent-test-override.yml", "spring.cloud.config.enabled=false", "spring.cloud.config.import-check.enabled=false",
        "spring.cloud.discovery.enabled=false", "eureka.client.enabled=false", "spring.flyway.enabled=false",
        "spring.kafka.listener.auto-startup=false", "commerce.outbox.enabled=false", "PAYMENT_PROVIDER=sandbox",
        "DB_URL=jdbc:h2:mem:payment-smoke;MODE=MySQL;DB_CLOSE_DELAY=-1", "DB_USERNAME=sa", "DB_PASSWORD=",
        "KAFKA_BOOTSTRAP_SERVERS=localhost:9092", "CONFIG_USERNAME=test", "CONFIG_PASSWORD=test",
        "EUREKA_URL=http://localhost:8761/eureka/", "INTERNAL_HMAC_SECRET=01234567890123456789012345678901"
})
class ApplicationSmokeTest {
    @Autowired ApplicationContext context;
    @Test void createsKafkaAndTransactionInfrastructureWithoutExternalServices() {
        assertNotNull(context.getBean(KafkaTemplate.class));
        assertNotNull(context.getBean(TransactionTemplate.class));
        assertNotNull(context.getBean(PaymentService.class));
        assertEquals(1,context.getBean(KafkaListenerEndpointRegistry.class).getListenerContainers().size());
        assertTrue(context.getBeansOfType(OutboxPublisher.class).isEmpty());
    }
}
