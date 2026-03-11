package com.work.mautonlaundry.services;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WebSocketMetricsTracker {
    private final AtomicInteger connections = new AtomicInteger(0);

    public WebSocketMetricsTracker(MeterRegistry registry) {
        registry.gauge("websocket_connections", connections);
    }

    @EventListener
    public void onSessionConnect(SessionConnectEvent event) {
        connections.incrementAndGet();
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        connections.decrementAndGet();
    }

    @EventListener
    public void onBrokerAvailability(BrokerAvailabilityEvent event) {
        if (!event.isBrokerAvailable()) {
            connections.set(0);
        }
    }
}
