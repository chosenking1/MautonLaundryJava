package com.work.mautonlaundry.data.model;

import lombok.Getter;

@Getter
public enum LaundryStatus {
    PENDING("Pending"),
    WASHING("Washing"),
    READY_FOR_PICKUP("Ready for Pickup"),
    DELIVERED("Delivered");

    private final String displayName;

   LaundryStatus(String displayName) {
        this.displayName = displayName;
    }


}
