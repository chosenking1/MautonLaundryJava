package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.Discount;
import com.work.mautonlaundry.data.model.DiscountUsageLog;
import com.work.mautonlaundry.data.model.DiscountUserAssignment;
import com.work.mautonlaundry.data.model.enums.DiscountCheckResult;
import com.work.mautonlaundry.data.model.enums.DiscountType;
import com.work.mautonlaundry.data.model.enums.ResetPeriod;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.DiscountRepository;
import com.work.mautonlaundry.data.repository.DiscountUsageLogRepository;
import com.work.mautonlaundry.data.repository.DiscountUserAssignmentRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.responses.discount.DiscountApplicationResult;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

    @Mock
    private DiscountRepository discountRepository;
    @Mock
    private DiscountUserAssignmentRepository assignmentRepository;
    @Mock
    private DiscountUsageLogRepository usageLogRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private DiscountService discountService;

    private AppUser owner;
    private Booking booking;
    private Discount discount;

    @BeforeEach
    void setUp() {
        owner = new AppUser();
        owner.setId("user-1");
        owner.setEmail("owner@example.com");

        booking = new Booking();
        booking.setId("booking-1");
        booking.setUser(owner);
        booking.setTotalPrice(new BigDecimal("1000.00"));
        booking.setFinalAmount(new BigDecimal("1000.00"));

        discount = Discount.builder()
                .id("discount-1")
                .code("SAVE10")
                .name("Ten Off")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .maxUsesPerUser(2)
                .currentTotalUses(0)
                .resetPeriod(ResetPeriod.NEVER)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(1))
                .active(true)
                .requiresApproval(false)
                .build();
    }

    @Test
    void applyDiscountPersistsDiscountOnBookingAndUsesCanonicalBookingTotal() {
        when(bookingRepository.findByIdAndDeletedFalseForUpdate("booking-1")).thenReturn(Optional.of(booking));
        when(discountRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(discount));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(owner));
        when(assignmentRepository.findByDiscountIdAndUserId("discount-1", "user-1"))
                .thenReturn(Optional.empty(), Optional.empty());
        when(assignmentRepository.save(any(DiscountUserAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(discountRepository.save(any(Discount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(usageLogRepository.save(any(DiscountUsageLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DiscountApplicationResult result = discountService.applyDiscountAtCheckout(
                "SAVE10", "user-1", "booking-1", new BigDecimal("999.00")
        );

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getResult()).isEqualTo(DiscountCheckResult.VALID);
        assertThat(result.getDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(result.getFinalOrderValue()).isEqualByComparingTo("900.00");
        assertThat(booking.getDiscountCode()).isEqualTo("SAVE10");
        assertThat(booking.getDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(booking.getFinalAmount()).isEqualByComparingTo("900.00");

        ArgumentCaptor<DiscountUsageLog> usageLogCaptor = ArgumentCaptor.forClass(DiscountUsageLog.class);
        verify(usageLogRepository).save(usageLogCaptor.capture());
        assertThat(usageLogCaptor.getValue().getOrderValueBefore()).isEqualByComparingTo("1000.00");
        assertThat(usageLogCaptor.getValue().getOrderValueAfter()).isEqualByComparingTo("900.00");
    }

    @Test
    void applyDiscountRejectsApplyingDiscountToAnotherUsersBooking() {
        AppUser otherUser = new AppUser();
        otherUser.setId("other-user");
        booking.setUser(otherUser);

        when(bookingRepository.findByIdAndDeletedFalseForUpdate("booking-1")).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> discountService.applyDiscountAtCheckout(
                "SAVE10", "user-1", "booking-1", new BigDecimal("1000.00")
        ))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("own bookings");

        verifyNoInteractions(discountRepository, usageLogRepository, assignmentRepository);
    }
}
