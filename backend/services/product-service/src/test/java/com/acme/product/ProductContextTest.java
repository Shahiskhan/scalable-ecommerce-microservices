package com.acme.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Verifies Boot 4's modular auto-configuration without requiring external infrastructure. */
@SpringBootTest(properties={
        "spring.cloud.config.enabled=false", "CONFIG_IMPORT=optional:classpath:/absent-test-override.yml", "spring.cloud.discovery.enabled=false", "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:context;MODE=MySQL;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.kafka.listener.auto-startup=false",
        "commerce.internal-hmac-secret=context-test-secret-minimum-thirty-two-bytes", "commerce.outbox-delay-ms=3600000",
        "spring.data.redis.password=not-used", "management.health.redis.enabled=false", "management.health.kafka.enabled=false"
})
class ProductContextTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired StringRedisTemplate redis;
    @Test void infrastructureBeansAreAvailable() {
        assertNotNull(jdbc); assertNotNull(kafka); assertNotNull(redis);
    }
}
