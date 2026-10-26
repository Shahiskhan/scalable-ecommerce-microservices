package com.acme.product.messaging;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

public record SagaEvent(String eventId, String eventType, String aggregateId, JsonNode payload) {
    public static SagaEvent read(ObjectMapper json, String recordKey, String data) {
        JsonNode node = json.readTree(data);
        String type = text(node, "eventType");
        if (!node.path("schemaVersion").isIntegralNumber() || node.path("schemaVersion").intValue() != 1) throw new IllegalArgumentException("Unsupported event schema");
        String eventId = uuid(node, "eventId"), aggregateId = uuid(node, "aggregateId");
        Instant.parse(text(node, "occurredAt"));
        JsonNode payload = node.path("payload");
        if (!payload.isObject() || !aggregateId.equals(uuid(payload, "orderId")) || !aggregateId.equals(recordKey)) throw new IllegalArgumentException("Event key/aggregate mismatch");
        String userId = text(payload, "userId");
        if (userId.length() > 128 || userId.contains("\n") || userId.contains("\r")) throw new IllegalArgumentException("Invalid user ID");
        return new SagaEvent(eventId, type, aggregateId, payload);
    }
    public static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isString() || value.stringValue().isBlank()) throw new IllegalArgumentException("Missing text field: " + field);
        return value.stringValue();
    }
    private static String uuid(JsonNode node, String field) {
        String value = text(node, field);
        if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException("Invalid UUID");
        return value;
    }
}
