package com.acme.product.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** A reservation decision uses catalog prices captured under database row locks. */
public record InventoryPlan(List<Line> lines, long totalMinor) {
    public record Item(String sku, int quantity) {}
    public record Line(String sku, int quantity, long unitPriceMinor) {}
    public static InventoryPlan prepare(List<Item> items, Map<String, Product> catalog, Instant expiresAt, Instant now) {
        if (!expiresAt.isAfter(now)) throw new Rejected("ORDER_EXPIRED");
        if (items.isEmpty() || items.size() > 20) throw new Rejected("INVALID_ITEMS");
        var seen = new HashSet<String>();
        var lines = new java.util.ArrayList<Line>();
        long total = 0;
        for (var item : items.stream().sorted(java.util.Comparator.comparing(Item::sku, java.util.Comparator.nullsLast(String::compareTo))).toList()) {
            if (item.sku() == null || !item.sku().matches("[A-Z0-9_-]{1,64}") || !seen.add(item.sku())
                    || item.quantity() < 1 || item.quantity() > 1000) throw new Rejected("INVALID_ITEMS");
            Product product = catalog.get(item.sku());
            if (product == null) throw new Rejected("UNKNOWN_PRODUCT");
            if (!"USD".equals(product.currency())) throw new Rejected("UNSUPPORTED_CURRENCY");
            if (product.availableStock() < item.quantity()) throw new Rejected("INSUFFICIENT_STOCK");
            try { total = Math.addExact(total, Math.multiplyExact(product.priceMinor(), item.quantity())); }
            catch (ArithmeticException overflow) { throw new Rejected("AMOUNT_TOO_LARGE"); }
            lines.add(new Line(item.sku(), item.quantity(), product.priceMinor()));
        }
        return new InventoryPlan(List.copyOf(lines), total);
    }
    public static final class Rejected extends RuntimeException {
        public Rejected(String reason) { super(reason); }
    }
}
