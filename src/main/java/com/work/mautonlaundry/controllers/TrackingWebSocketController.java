package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.requests.trackingrequests.DeliveryLocationUpdateMessage;
import com.work.mautonlaundry.services.TrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class TrackingWebSocketController {
    private final TrackingService trackingService;

    @MessageMapping("/location")
    public void handleLocationUpdate(DeliveryLocationUpdateMessage message) {
        trackingService.handleLocationUpdate(message);
    }
}
