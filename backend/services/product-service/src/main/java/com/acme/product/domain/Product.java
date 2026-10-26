package com.acme.product.domain;

public record Product(String sku, String name, long priceMinor, String currency, int availableStock) {}
