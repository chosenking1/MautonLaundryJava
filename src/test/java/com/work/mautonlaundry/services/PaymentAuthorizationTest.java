package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.Payment;
import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.model.enums.PaymentMethod;
import com.work.mautonlaundry.data.model.enums.PaymentProvider;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.PaymentRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.requests.paymentrequests.InitiateGatewayPaymentRequest;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.payments.PaymentGatewayRouter;
import com.work.mautonlaundry.services.payments.PaymentGatewayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentAuthorizationTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentGatewayRouter gatewayRouter;
    @Mock
    private BookingService bookingService;
    @Mock
    private ReferralService referralService;

    private PaymentServiceImpl paymentService;
    private PaymentGatewayService paymentGatewayService;
    private AppUser currentUser;
    private Booking foreignBooking;

    @BeforeEach
    void setUp() {
        SecurityUtil securityUtil = new SecurityUtil();
        securityUtil.setUserRepository(userRepository);

        currentUser = new AppUser();
        currentUser.setId("current-user");
        currentUser.setEmail("current@example.com");
        currentUser.setRole(new Role("USER"));

        AppUser owner = new AppUser();
        owner.setId("owner-user");
        owner.setEmail("owner@example.com");
        owner.setRole(new Role("USER"));

        foreignBooking = new Booking();
        foreignBooking.setId("booking-1");
        foreignBooking.setUser(owner);
        foreignBooking.setTotalPrice(new BigDecimal("5000.00"));
        foreignBooking.setFinalAmount(new BigDecimal("4500.00"));

        var principal = User.withUsername(currentUser.getEmail())
                .password("ignored")
                .authorities(List.of())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        when(userRepository.findUserByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));

        paymentService = new PaymentServiceImpl(paymentRepository, bookingRepository);
        paymentGatewayService = new PaymentGatewayService(paymentRepository, bookingRepository, gatewayRouter, bookingService, referralService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getPaymentByBookingIdRejectsReadingAnotherUsersPayment() {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setBooking(foreignBooking);
        payment.setAmount(new BigDecimal("4500.00"));
        payment.setPaymentMethod(PaymentMethod.CARD);
        payment.setStatus(PaymentStatus.PENDING);

        when(paymentRepository.findByBooking_Id("booking-1")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.getPaymentByBookingId("booking-1"))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void initiateGatewayPaymentRejectsAnotherUsersBooking() {
        InitiateGatewayPaymentRequest request = new InitiateGatewayPaymentRequest();
        request.setBookingId("booking-1");
        request.setAmount(new BigDecimal("4500.00"));
        request.setProvider(PaymentProvider.PAYSTACK);
        request.setPaymentMethod(PaymentMethod.CARD);

        when(bookingRepository.findByIdAndDeletedFalse("booking-1")).thenReturn(Optional.of(foreignBooking));

        assertThatThrownBy(() -> paymentGatewayService.initiatePayment(request))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Access denied");

        verifyNoInteractions(gatewayRouter);
    }
}
