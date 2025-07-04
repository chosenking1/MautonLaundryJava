package com.work.mautonlaundry.data.model;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    PENDING ("Pending"),
    COMPLETED ("Completed"),
    FAILED ("Failed"),
    REFUNDED ("Refunded");


    private final String status;

    PaymentStatus(String status) {
        this.status = status;
    }
}
