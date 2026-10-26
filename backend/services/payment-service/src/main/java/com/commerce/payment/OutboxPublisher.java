package com.commerce.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name="commerce.outbox.enabled",havingValue="true",matchIfMissing=true)
public class OutboxPublisher {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String,String> kafka;
    private final String topic;
    public OutboxPublisher(JdbcTemplate db,TransactionTemplate tx,KafkaTemplate<String,String> kafka,@Value("${commerce.topic}") String topic) {
        this.db=db; this.tx=new TransactionTemplate(java.util.Objects.requireNonNull(tx.getTransactionManager()));
        this.tx.setTimeout(25); this.kafka=kafka; this.topic=topic;
    }
    @Scheduled(fixedDelayString="${commerce.outbox-delay-ms:500}")
    public void publish() {
        // A crash after the Kafka ack and before the SQL commit deliberately yields a duplicate.
        tx.executeWithoutResult(status->{
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            var rows=db.queryForList("SELECT id,aggregate_id,body FROM outbox WHERE published_at IS NULL ORDER BY id LIMIT 25 FOR UPDATE SKIP LOCKED");
            record Pending(Object id,java.util.concurrent.CompletableFuture<org.springframework.kafka.support.SendResult<String,String>> ack) { }
            var pending=new java.util.ArrayList<Pending>();
            for(var row:rows) {
                if (System.nanoTime()>=deadline) throw new IllegalStateException("Outbox batch deadline exceeded");
                // Producer max.block.ms is bounded to 3000 in application.yml.
                pending.add(new Pending(row.get("id"),kafka.send(topic,row.get("aggregate_id").toString(),row.get("body").toString())));
            }
            for(var message:pending) {
                long remaining=deadline-System.nanoTime();
                if (remaining<=0) throw new IllegalStateException("Outbox batch deadline exceeded");
                try { message.ack().get(remaining,TimeUnit.NANOSECONDS); }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Outbox interrupted",interrupted); }
                catch(Exception failure) { throw new IllegalStateException("Outbox publication failed",failure); }
                db.update("UPDATE outbox SET published_at=? WHERE id=?",Timestamp.from(Instant.now()),message.id());
            }
        });
    }
}

