package com.work.mautonlaundry.services.payments;

import com.work.mautonlaundry.data.model.enums.PaymentProvider;
import com.work.mautonlaundry.payments.gateway.PaymentGatewayAdapter;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Component
public class PaymentGatewayRouter {
    private final Map<PaymentProvider, PaymentGatewayAdapter> adapters = new EnumMap<>(PaymentProvider.class);

    public PaymentGatewayRouter(List<PaymentGatewayAdapter> adapters) {
        for (PaymentGatewayAdapter adapter : adapters) {
            this.adapters.put(adapter.provider(), adapter);
        }
    }

    public PaymentGatewayAdapter resolve(PaymentProvider provider) {
        PaymentGatewayAdapter adapter = adapters.get(provider);
        if (adapter == null) {
            throw new NoSuchElementException("Unsupported payment provider: " + provider);
        }
        return adapter;
    }
}
