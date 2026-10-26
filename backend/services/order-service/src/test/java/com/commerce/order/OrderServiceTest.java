package com.commerce.order;

import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {
    JdbcTemplate db; OrderService service; ObjectMapper json=JsonMapper.builder().build();
    @BeforeEach void setup() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        String schema=new ClassPathResource("db/migration/V1__order_domain.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                .replace(" DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin","");
        new ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(schema.getBytes(java.nio.charset.StandardCharsets.UTF_8))).execute(source);
        db=new JdbcTemplate(source);
        service=new OrderService(db,new TransactionTemplate(new DataSourceTransactionManager(source)),json,Duration.ofMinutes(15));
    }
    OrderService.OrderView place() { return service.place("alice","key-1",new OrderService.PlaceOrder(List.of(new OrderService.Item("SKU",2)))); }
    void event(String type,String order,Map<String,Object> payload) { service.accept(json.valueToTree(SagaEvent.create(type,order,payload))); }
    @Test void retryCreatesOneOrderAndOneEventAndRejectsDifferentBody() {
        var first=place(); assertEquals(first,place());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM outbox",Integer.class));
        var failure=assertThrows(ResponseStatusException.class,()->service.place("alice","key-1",new OrderService.PlaceOrder(List.of(new OrderService.Item("SKU",3)))));
        assertEquals(409,failure.getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.readOwned(first.orderId(),"bob")).getStatusCode().value());
    }
    @Test void requestValidationMatchesInventoryItemAndSkuContract() {
        try (var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            assertFalse(validator.validate(new OrderService.PlaceOrder(java.util.stream.IntStream.range(0,21)
                    .mapToObj(i->new OrderService.Item("SKU-"+i,1)).toList())).isEmpty());
            assertFalse(validator.validate(new OrderService.PlaceOrder(List.of(new OrderService.Item("lower.case",1)))).isEmpty());
            assertFalse(validator.validate(new OrderService.PlaceOrder(java.util.Arrays.asList((OrderService.Item)null))).isEmpty());
            assertTrue(validator.validate(new OrderService.PlaceOrder(List.of(new OrderService.Item("SKU_1",1)))).isEmpty());
        }
    }
    @Test void paymentConfirmsOnceAndLateFailureCannotRegress() {
        var order=place(); var money=Map.<String,Object>of("orderId",order.orderId(),"userId","alice","totalMinor",1200L,"currency","USD");
        event("InventoryReserved",order.orderId(),money);
        var authorized=json.valueToTree(SagaEvent.create("PaymentAuthorized",order.orderId(),money));
        service.accept(authorized); service.accept(authorized);
        event("PaymentFailed",order.orderId(),Map.of("orderId",order.orderId(),"userId","alice","reason","late"));
        assertEquals("CONFIRMED",service.readOwned(order.orderId(),"alice").status());
        assertEquals(1200L,service.readOwned(order.orderId(),"alice").totalMinor());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM outbox",Integer.class));
    }
    @Test void failedPaymentCancelsOnceAndInvalidAmountRollsBackInbox() {
        var order=place();
        var invalid=Map.<String,Object>of("orderId",order.orderId(),"userId","alice","totalMinor",-1L,"currency","USD");
        assertThrows(IllegalArgumentException.class,()->event("InventoryReserved",order.orderId(),invalid));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM inbox",Integer.class));
        event("PaymentFailed",order.orderId(),Map.of("orderId",order.orderId(),"userId","alice","reason","declined"));
        assertEquals("CANCELLED",service.readOwned(order.orderId(),"alice").status());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM outbox",Integer.class));
    }
}
