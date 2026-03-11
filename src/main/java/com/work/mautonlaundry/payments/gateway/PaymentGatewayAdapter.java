package com.work.mautonlaundry.payments.gateway;

import com.work.mautonlaundry.data.model.Payment;
import com.work.mautonlaundry.data.model.enums.PaymentProvider;
import com.work.mautonlaundry.payments.gateway.model.GatewayInitiationResult;
import com.work.mautonlaundry.payments.gateway.model.GatewayWebhookEvent;

import java.util.Map;

public interface PaymentGatewayAdapter {
    PaymentProvider provider();

    GatewayInitiationResult initiatePayment(Payment payment);

    GatewayWebhookEvent verifyAndParseWebhook(Map<String, String> headers, String payload);
}
