package com.work.mautonlaundry.data.model;

import lombok.Getter;

@Getter
public enum PaymentMethod {
    CARD("Card"),
    CASH("Cash"),
    TRANSFER("Transfer");


    private final String paymentMethod;

    PaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
