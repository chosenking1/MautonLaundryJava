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
import com.work.mautonlaundry.dtos.requests.paymentrequests.CreatePaymentRequest;
import com.work.mautonlaundry.dtos.requests.paymentrequests.InitiateGatewayPaymentRequest;
import com.work.mautonlaundry.payments.gateway.PaymentGatewayAdapter;
import com.work.mautonlaundry.payments.gateway.model.GatewayInitiationResult;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.payments.PaymentGatewayRouter;
import com.work.mautonlaundry.services.payments.PaymentGatewayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentFlowAmountSelectionTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentGatewayRouter gatewayRouter;
    @Mock
    private PaymentGatewayAdapter gatewayAdapter;
    @Mock
    private BookingService bookingService;
    @Mock
    private ReferralService referralService;

    private PaymentServiceImpl paymentService;
    private PaymentGatewayService paymentGatewayService;
    private AppUser currentUser;
    private Booking booking;

    @BeforeEach
    void setUp() {
        SecurityUtil securityUtil = new SecurityUtil();
        securityUtil.setUserRepository(userRepository);

        currentUser = new AppUser();
        currentUser.setId("user-1");
        currentUser.setEmail("user@example.com");
        currentUser.setRole(new Role("USER"));

        booking = new Booking();
        booking.setId("booking-1");
        booking.setUser(currentUser);
        booking.setTotalPrice(new BigDecimal("2000.00"));
        booking.setFinalAmount(new BigDecimal("1500.00"));

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
    void createPaymentUsesPersistedFinalAmountWhenPresent() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setBookingId("booking-1");
        request.setAmount(new BigDecimal("2000.00"));
        request.setPaymentMethod(PaymentMethod.CARD);

        when(bookingRepository.findByIdAndDeletedFalse("booking-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking_Id("booking-1")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(1L);
            return payment;
        });

        var response = paymentService.createPayment(request);

        assertThat(response.getAmount()).isEqualByComparingTo("1500.00");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo("1500.00");
    }

    @Test
    void initiateGatewayPaymentUsesPersistedFinalAmountWhenPresent() {
        InitiateGatewayPaymentRequest request = new InitiateGatewayPaymentRequest();
        request.setBookingId("booking-1");
        request.setAmount(new BigDecimal("2000.00"));
        request.setProvider(PaymentProvider.PAYSTACK);
        request.setPaymentMethod(PaymentMethod.CARD);

        when(bookingRepository.findByIdAndDeletedFalse("booking-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking_Id("booking-1")).thenReturn(Optional.empty());
        when(gatewayRouter.resolve(PaymentProvider.PAYSTACK)).thenReturn(gatewayAdapter);
        when(gatewayAdapter.initiatePayment(any(Payment.class)))
                .thenReturn(new GatewayInitiationResult("ref-123", "https://checkout", "acc-1", "{}"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(22L);
            return payment;
        });

        var response = paymentGatewayService.initiatePayment(request);

        assertThat(response.getPaymentId()).isEqualTo(22L);
        assertThat(response.getGatewayReference()).isEqualTo("ref-123");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(gatewayAdapter).initiatePayment(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo("1500.00");
    }
}
