package com.commerce.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Configuration
public class PaymentConfiguration {
    @Bean
    PaymentProvider paymentProvider(Environment environment,
                                    @Value("${PAYMENT_PROVIDER:unconfigured}") String provider,
                                    @Value("${SANDBOX_PAYMENT_DECLINE:false}") boolean decline) {
        if (!java.util.Arrays.equals(environment.getActiveProfiles(),new String[]{"local"}) || !provider.equals("sandbox"))
            throw new IllegalStateException("Payment provider is not configured. Sandbox requires local profile and PAYMENT_PROVIDER=sandbox; implement a real idempotent provider before production.");
        return (order,amount,currency)->new PaymentProvider.Decision(!decline,
                "sandbox-"+UUID.nameUUIDFromBytes(order.getBytes(StandardCharsets.UTF_8)),decline?"SandboxDeclined":"");
    }
}
