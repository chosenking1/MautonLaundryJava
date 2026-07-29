package com.work.mautonlaundry.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.DeliveryAssignment;
import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.DeliveryAssignmentRepository;
import com.work.mautonlaundry.data.repository.DeliveryRouteHistoryRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.requests.trackingrequests.DeliveryLocationUpdateMessage;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackingServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DeliveryAssignmentRepository deliveryAssignmentRepository;
    @Mock
    private DeliveryRouteHistoryRepository deliveryRouteHistoryRepository;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private GeoOperations<String, String> geoOperations;

    @Test
    void handleLocationUpdateRequiresAuthenticatedPrincipal() {
        TrackingService service = new TrackingService(
                redisTemplate,
                new ObjectMapper(),
                messagingTemplate,
                bookingRepository,
                userRepository,
                deliveryAssignmentRepository,
                deliveryRouteHistoryRepository
        );

        DeliveryLocationUpdateMessage message = new DeliveryLocationUpdateMessage();
        message.setBookingId("booking-1");
        message.setAgentId("spoofed-agent");
        message.setLatitude(6.5);
        message.setLongitude(3.3);
        message.setTimestamp(1700L);

        assertThatThrownBy(() -> service.handleLocationUpdate(message, null))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Authenticated WebSocket session is required");
    }

    @Test
    void handleLocationUpdateUsesPrincipalIdentityAndPublishesTrackingPayload() {
        TrackingService service = new TrackingService(
                redisTemplate,
                new ObjectMapper(),
                messagingTemplate,
                bookingRepository,
                userRepository,
                deliveryAssignmentRepository,
                deliveryRouteHistoryRepository
        );

        Booking booking = new Booking();
        booking.setId("booking-1");

        AppUser agent = new AppUser();
        agent.setId("actual-agent");
        agent.setEmail("agent@example.com");
        agent.setRole(new Role("DELIVERY_AGENT"));

        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setStatus(DeliveryAssignmentStatus.ACCEPTED);

        DeliveryLocationUpdateMessage message = new DeliveryLocationUpdateMessage();
        message.setBookingId("booking-1");
        message.setAgentId("spoofed-agent");
        message.setLatitude(6.465422);
        message.setLongitude(3.406448);
        message.setTimestamp(1700L);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(userRepository.findUserByEmail("agent@example.com")).thenReturn(Optional.of(agent));
        when(bookingRepository.findByIdAndDeletedFalse("booking-1")).thenReturn(Optional.of(booking));
        when(deliveryAssignmentRepository.findByBookingAndDeliveryAgent(booking, agent)).thenReturn(Optional.of(assignment));
        when(geoOperations.add(anyString(), any(Point.class), anyString())).thenReturn(1L);

        Principal principal = () -> "agent@example.com";
        service.handleLocationUpdate(message, principal);

        verify(userRepository).findUserByEmail("agent@example.com");
        verify(deliveryRouteHistoryRepository).save(any());
        verify(messagingTemplate).convertAndSend(eq("/topic/tracking/booking-1"), org.mockito.ArgumentMatchers.<Object>any());
        verify(valueOperations, atLeastOnce()).set(anyString(), anyString());
        verify(geoOperations).add(eq("delivery_agents_geo"), any(Point.class), eq("actual-agent"));
    }
}
