package com.acme.commerce.user;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.cloud.config.enabled=false", "CONFIG_IMPORT=optional:classpath:/absent-test-override.yml", "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:usercontext;MODE=MySQL;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=false", "security.internal-hmac-secret=0123456789abcdef0123456789abcdef",
    "spring.kafka.bootstrap-servers=127.0.0.1:19092", "spring.kafka.listener.auto-startup=false",
    "CONFIG_USERNAME=test", "CONFIG_PASSWORD=test", "DB_URL=jdbc:h2:mem:usercontext", "DB_USERNAME=sa", "DB_PASSWORD=",
    "EUREKA_URL=http://127.0.0.1:18761/eureka/", "INTERNAL_HMAC_SECRET=0123456789abcdef0123456789abcdef",
    "KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:19092"
})
class ApplicationContextTest {
    @Autowired com.acme.commerce.user.application.ProfileService profiles;
    @Test void profileWiringIsComplete() { assertThat(profiles).isNotNull(); }
}
