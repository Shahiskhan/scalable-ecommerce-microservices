package com.commerce.payment;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class PaymentConfigurationTest {
    @Test void sandboxRequiresExplicitLocalProfileAndHasStableProviderKey() {
        var config=new PaymentConfiguration();
        assertThrows(IllegalStateException.class,()->config.paymentProvider(new MockEnvironment(),"sandbox",false));
        var environment=new MockEnvironment(); environment.setActiveProfiles("production");
        assertThrows(IllegalStateException.class,()->config.paymentProvider(environment,"sandbox",false));
        environment.setActiveProfiles("local","production");
        assertThrows(IllegalStateException.class,()->config.paymentProvider(environment,"sandbox",false));
        environment.setActiveProfiles("local");
        assertThrows(IllegalStateException.class,()->config.paymentProvider(environment,"unconfigured",false));
        var provider=config.paymentProvider(environment,"sandbox",false);
        assertEquals(provider.authorize("order-1",100,"USD"),provider.authorize("order-1",100,"USD"));
        assertFalse(config.paymentProvider(environment,"sandbox",true).authorize("order-1",100,"USD").authorized());
    }
}
