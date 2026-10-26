package com.acme.product.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class InventoryPlanTest {
    private final Instant now = Instant.parse("2026-10-26T12:00:00Z");
    private final Map<String, Product> catalog = Map.of("A", new Product("A", "A", 299, "USD", 3));
    @Test void computesAmountFromAuthoritativeUnitPrice() {
        var plan = InventoryPlan.prepare(List.of(new InventoryPlan.Item("A", 2)), catalog, now.plusSeconds(60), now);
        assertEquals(598, plan.totalMinor());
        assertEquals(299, plan.lines().getFirst().unitPriceMinor());
    }
    @Test void refusesInsufficientStockBeforeMutation() {
        var error = assertThrows(InventoryPlan.Rejected.class, () -> InventoryPlan.prepare(List.of(new InventoryPlan.Item("A", 4)), catalog, now.plusSeconds(60), now));
        assertEquals("INSUFFICIENT_STOCK", error.getMessage());
    }
    @Test void rejectsDuplicatesRatherThanDoubleCountingStock() {
        assertThrows(InventoryPlan.Rejected.class, () -> InventoryPlan.prepare(List.of(new InventoryPlan.Item("A", 2), new InventoryPlan.Item("A", 2)), catalog, now.plusSeconds(60), now));
    }
    @Test void rejectsExpiredOrdersAndNonpositiveQuantities() {
        assertThrows(InventoryPlan.Rejected.class, () -> InventoryPlan.prepare(List.of(new InventoryPlan.Item("A", 1)), catalog, now, now));
        assertThrows(InventoryPlan.Rejected.class, () -> InventoryPlan.prepare(List.of(new InventoryPlan.Item("A", 0)), catalog, now.plusSeconds(60), now));
    }
    @Test void rejectsOverflowingMoneyCalculation() {
        var huge = Map.of("A", new Product("A", "A", Long.MAX_VALUE, "USD", 3));
        var error = assertThrows(InventoryPlan.Rejected.class, () -> InventoryPlan.prepare(List.of(new InventoryPlan.Item("A", 2)), huge, now.plusSeconds(60), now));
        assertEquals("AMOUNT_TOO_LARGE", error.getMessage());
    }
}
