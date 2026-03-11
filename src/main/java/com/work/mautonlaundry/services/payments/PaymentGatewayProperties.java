package com.work.mautonlaundry.services.payments;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payments.gateway")
public class PaymentGatewayProperties {
    private ProviderProperties monnify = new ProviderProperties();
    private ProviderProperties squad = new ProviderProperties();
    private ProviderProperties paystack = new ProviderProperties();

    @Getter
    @Setter
    public static class ProviderProperties {
        private String baseUrl;
        private String publicKey;
        private String secretKey;
        private String merchantId;
        private String contractCode;
        private String webhookSecret;
    }
}
