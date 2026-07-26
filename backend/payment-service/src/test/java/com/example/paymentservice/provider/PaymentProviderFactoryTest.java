package com.example.paymentservice.provider;

import com.example.paymentservice.entity.PaymentProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProviderFactoryTest {

    @Test
    void get_registeredProvider_returnsItsAdapter() {
        PaymentProviderAdapter stripeAdapter = Mockito.mock(PaymentProviderAdapter.class);
        Mockito.when(stripeAdapter.getProvider()).thenReturn(PaymentProvider.STRIPE);

        PaymentProviderFactory factory = new PaymentProviderFactory(List.of(stripeAdapter));

        assertThat(factory.get(PaymentProvider.STRIPE)).isSameAs(stripeAdapter);
    }

    @Test
    void get_unregisteredProvider_throwsNotConfigured() {
        PaymentProviderFactory factory = new PaymentProviderFactory(List.of());

        assertThatThrownBy(() -> factory.get(PaymentProvider.ROCKET_LINE))
                .isInstanceOf(PaymentProviderNotConfiguredException.class);
    }
}
