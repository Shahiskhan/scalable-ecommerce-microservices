package com.commerce.payment;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SagaEvent(String eventId, String eventType, int schemaVersion, String aggregateId,
                        String occurredAt, Map<String, Object> payload) {
    static SagaEvent create(String type, String orderId, Map<String, Object> payload) {
        return new SagaEvent(UUID.randomUUID().toString(), type, 1, orderId, Instant.now().toString(), payload);
    }
    static JsonNode parse(ObjectMapper mapper, String body) {
        JsonNode event = mapper.readTree(body);
        if (event.path("schemaVersion").asInt() != 1 || !event.path("payload").isObject()
                || !event.path("eventType").isTextual()) throw new IllegalArgumentException("Invalid event envelope");
        UUID.fromString(event.required("eventId").asText());
        UUID.fromString(event.required("aggregateId").asText());
        Instant.parse(event.required("occurredAt").asText());
        if (!event.path("aggregateId").asText().equals(event.path("payload").path("orderId").asText()))
            throw new IllegalArgumentException("Event aggregate mismatch");
        return event;
    }
}

