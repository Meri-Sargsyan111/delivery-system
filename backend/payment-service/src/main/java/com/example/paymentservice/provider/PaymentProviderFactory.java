package com.example.paymentservice.provider;

import com.example.paymentservice.entity.PaymentProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Indexes every registered PaymentProviderAdapter bean by its PaymentProvider - Spring
 * autowires the full list, this just builds the lookup map once. This is the whole
 * extensibility point: a new provider is a new adapter bean, nothing here changes.
 */
@Slf4j
@Component
public class PaymentProviderFactory {

    private final Map<PaymentProvider, PaymentProviderAdapter> adapters;

    public PaymentProviderFactory(List<PaymentProviderAdapter> adapterList) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(PaymentProviderAdapter::getProvider, Function.identity()));
        log.info("Registered payment provider adapters: {}", adapters.keySet());
    }

    public PaymentProviderAdapter get(PaymentProvider provider) {
        PaymentProviderAdapter adapter = adapters.get(provider);
        if (adapter == null) {
            throw new PaymentProviderNotConfiguredException("No adapter registered for provider: " + provider);
        }
        return adapter;
    }
}
