package com.work.mautonlaundry.data.model.enums;

import lombok.Getter;

@Getter
public enum PaymentMethod {
    CARD("Card"),
    TRANSFER("Transfer");


    private final String paymentMethod;

    PaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
