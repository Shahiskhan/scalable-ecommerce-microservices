package com.commerce.payment;

/** Provider calls must use orderId as the provider's idempotency key. Unknown outcomes must
 * be reconciled by that key; they must never be interpreted as a definitive decline. */
public interface PaymentProvider {
    record Decision(boolean authorized, String paymentId, String reason) { }
    Decision authorize(String orderId,long amountMinor,String currency);
}
