package com.commerce.payment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

@Service
public class PaymentService {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final PaymentProvider provider;
    public PaymentService(JdbcTemplate db,TransactionTemplate tx,ObjectMapper json,PaymentProvider provider) {
        this.db=db; this.tx=tx; this.json=json; this.provider=provider;
    }
    public void accept(JsonNode event) {
        if (!event.path("eventType").asText().equals("InventoryReserved")) return;
        tx.executeWithoutResult(status->{
            if (db.update("INSERT IGNORE INTO inbox(event_id,received_at) VALUES(?,?)",event.required("eventId").asText(),Timestamp.from(Instant.now()))==0) return;
            JsonNode payload=event.required("payload");
            String order=event.required("aggregateId").asText(), user=payload.required("userId").asText();
            if (user.isBlank() || user.length()>128 || !payload.path("totalMinor").isIntegralNumber()
                    || !payload.path("totalMinor").canConvertToLong() || payload.path("totalMinor").asLong()<=0
                    || !"USD".equals(payload.path("currency").asText())) throw new IllegalArgumentException("Invalid reserved payment");
            long amount=payload.path("totalMinor").asLong();
            Instant expires=Instant.parse(payload.required("expiresAt").asText());
            // Lock on a unique order key also handles logically duplicated events with new event IDs.
            String payment="pending-"+order;
            db.update("INSERT INTO payments(order_id,payment_id,user_id,total_minor,currency,status,created_at) VALUES(?,?,?,?,?,'PENDING',?) ON DUPLICATE KEY UPDATE order_id=order_id",
                    order,payment,user,amount,"USD",Timestamp.from(Instant.now()));
            Map<String,Object> row=db.queryForMap("SELECT * FROM payments WHERE order_id=? FOR UPDATE",order);
            if (!user.equals(row.get("user_id")) || ((Number)row.get("total_minor")).longValue()!=amount || !"USD".equals(row.get("currency")))
                throw new IllegalArgumentException("Conflicting payment event");
            if (!row.get("status").equals("PENDING")) return;
            // The supplied sandbox is deterministic and performs no network IO. A real adapter requires
            // durable attempt/reconciliation workers; do not put unbounded network requests in this transaction.
            PaymentProvider.Decision decision=expires.isAfter(Instant.now()) ? provider.authorize(order,amount,"USD")
                    : new PaymentProvider.Decision(false,"expired-"+order,"ReservationExpired");
            db.update("UPDATE payments SET payment_id=?,status=? WHERE order_id=?",decision.paymentId(),decision.authorized()?"AUTHORIZED":"FAILED",order);
            Map<String,Object> result=decision.authorized()
                    ? Map.of("orderId",order,"userId",user,"paymentId",decision.paymentId(),"totalMinor",amount,"currency","USD")
                    : Map.of("orderId",order,"userId",user,"reason",decision.reason());
            SagaEvent output=SagaEvent.create(decision.authorized()?"PaymentAuthorized":"PaymentFailed",order,result);
            db.update("INSERT INTO outbox(event_id,aggregate_id,body,created_at) VALUES(?,?,?,?)",output.eventId(),order,json.writeValueAsString(output),Timestamp.from(Instant.now()));
        });
    }
}
