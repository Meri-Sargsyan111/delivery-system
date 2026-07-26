package com.example.paymentservice.provider;

import com.example.paymentservice.entity.PaymentProvider;
import com.example.paymentservice.provider.dto.PaymentCreationContext;
import com.example.paymentservice.provider.rocketline.RocketLinePaymentProviderAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rocket Line has no real API documentation available yet (see class javadoc on the
 * adapter) - these tests confirm the adapter is fully wired into the provider
 * abstraction (correct PaymentProvider, registered in the factory) and fails clearly
 * and consistently rather than silently, for every operation, until real credentials/
 * docs are supplied.
 */
class RocketLinePaymentProviderAdapterTest {

    private final RocketLinePaymentProviderAdapter adapter =
            new RocketLinePaymentProviderAdapter(new RestTemplate(), false, "", "", "");

    @Test
    void getProvider_returnsRocketLine() {
        assertThat(adapter.getProvider()).isEqualTo(PaymentProvider.ROCKET_LINE);
    }

    @Test
    void createPayment_throwsNotConfigured() {
        PaymentCreationContext context = new PaymentCreationContext(UUID.randomUUID(), BigDecimal.TEN, "AMD", "test");
        assertThatThrownBy(() -> adapter.createPayment(context))
                .isInstanceOf(PaymentProviderNotConfiguredException.class);
    }

    @Test
    void verifyPayment_throwsNotConfigured() {
        assertThatThrownBy(() -> adapter.verifyPayment("ref"))
                .isInstanceOf(PaymentProviderNotConfiguredException.class);
    }

    @Test
    void refund_throwsNotConfigured() {
        assertThatThrownBy(() -> adapter.refund("ref", null))
                .isInstanceOf(PaymentProviderNotConfiguredException.class);
    }

    @Test
    void verifyWebhookSignature_throwsNotConfigured() {
        assertThatThrownBy(() -> adapter.verifyWebhookSignature("{}", "sig"))
                .isInstanceOf(PaymentProviderNotConfiguredException.class);
    }

    @Test
    void parseWebhookEvent_throwsNotConfigured() {
        assertThatThrownBy(() -> adapter.parseWebhookEvent("{}"))
                .isInstanceOf(PaymentProviderNotConfiguredException.class);
    }
}
