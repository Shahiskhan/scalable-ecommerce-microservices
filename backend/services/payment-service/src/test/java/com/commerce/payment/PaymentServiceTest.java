package com.commerce.payment;

import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceTest {
    JdbcTemplate db; TransactionTemplate tx; ObjectMapper json=JsonMapper.builder().build();
    @BeforeEach void setup() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        String schema=new ClassPathResource("db/migration/V1__payment_domain.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                .replace(" DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin","");
        new ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(schema.getBytes(java.nio.charset.StandardCharsets.UTF_8))).execute(source);
        db=new JdbcTemplate(source); tx=new TransactionTemplate(new DataSourceTransactionManager(source));
    }
    JsonNode reserved(String id,Instant expiry) { return json.valueToTree(SagaEvent.create("InventoryReserved",id,Map.of("orderId",id,"userId","alice","totalMinor",200L,"currency","USD","expiresAt",expiry.toString()))); }
    @Test void duplicateSemanticEventsNeverAuthorizeTwice() {
        var calls=new AtomicInteger();
        var service=new PaymentService(db,tx,json,(order,amount,currency)->{calls.incrementAndGet();return new PaymentProvider.Decision(true,"provider-"+order,"");});
        var id=UUID.randomUUID().toString(); var event=reserved(id,Instant.now().plusSeconds(600));
        service.accept(event); service.accept(event); service.accept(reserved(id,Instant.now().plusSeconds(600)));
        assertEquals(1,calls.get()); assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM outbox",Integer.class));
        assertEquals("AUTHORIZED",db.queryForObject("SELECT status FROM payments",String.class));
    }
    @Test void expiredReservationDeclinesWithoutCallingProvider() {
        var service=new PaymentService(db,tx,json,(order,amount,currency)->{throw new AssertionError("Expired reservation charged");});
        service.accept(reserved(UUID.randomUUID().toString(),Instant.now().minusSeconds(1)));
        assertEquals("FAILED",db.queryForObject("SELECT status FROM payments",String.class));
        assertTrue(db.queryForObject("SELECT body FROM outbox",String.class).contains("ReservationExpired"));
    }
    @Test void unknownProviderOutcomeRollsBackInsteadOfPublishingFalseDecline() {
        var calls=new AtomicInteger();
        var service=new PaymentService(db,tx,json,(order,amount,currency)->{if(calls.incrementAndGet()==1)throw new IllegalStateException("Transport unavailable");return new PaymentProvider.Decision(true,"provider-"+order,"");});
        var event=reserved(UUID.randomUUID().toString(),Instant.now().plusSeconds(600));
        assertThrows(IllegalStateException.class,()->service.accept(event));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM inbox",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM outbox",Integer.class));
        service.accept(event);
        assertEquals("AUTHORIZED",db.queryForObject("SELECT status FROM payments",String.class));
    }
}
