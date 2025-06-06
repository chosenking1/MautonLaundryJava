package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.*;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.requests.bookingrequests.RegisterBookingRequest;
import com.work.mautonlaundry.dtos.requests.bookingrequests.UpdateBookingRequest;
import com.work.mautonlaundry.dtos.responses.bookingresponse.RegisterBookingResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.UpdateBookingResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.ViewBookingResponse;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.exceptions.serviceexceptions.ServiceNotFoundException;
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import jakarta.transaction.Transactional;

import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.springframework.util.ObjectUtils.isEmpty;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService{
    @Autowired
    private BookingRepository bookingRepository;

    ModelMapper mapper = new ModelMapper();

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PaymentRepository paymentRepository;
    private final DeliveryManagementRepository deliveryManagementRepository;

    @Override
    @Transactional
    public RegisterBookingResponse registerBooking(RegisterBookingRequest request) {
        // Get user
        User user = userRepository.findUserById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Create booking
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setDate_booked(LocalDateTime.now());
        booking.setLaundryStatus(LaundryStatus.PENDING);
        booking.setUrgency(request.getUrgency());

        // Save booking
        Booking savedBooking = bookingRepository.save(booking);

        // Create payment record
        Payment payment = createPayment(savedBooking, request);
        booking.setPayment(payment);

        // Setup delivery
//        createDeliveryManagement(request.getAddressId(), request);

        return mapper.map(savedBooking, RegisterBookingResponse.class);
    }

    private Payment createPayment(Booking method, RegisterBookingRequest booking) {
        Payment payment = new Payment();
        payment.setPaymentMethod(PaymentMethod.TRANSFER);
        payment.setAmount(totalPriceCalculation(booking.getService()));
        payment.setStatus(PaymentStatus.PENDING);

        payment.setBooking(method);
//        payment.setUser(booking.getUser());
        return paymentRepository.save(payment);
    }

    private void createDeliveryManagement(Long addressId, Booking booking) {
//        Address address = addressRepository.findById(addressId)
//                .orElseThrow(() -> new AddressNotFoundException("Address not found"));

        DeliveryManagement delivery = new DeliveryManagement();
        delivery.setBooking_id(booking.getId());
        delivery.setAddress(booking.getUser().getAddress());
        delivery.setDeliveryStatus(DeliveryStatus.PENDING_PICKUP);
        deliveryManagementRepository.save(delivery);
    }

    private BigDecimal totalPriceCalculation(JSONArray service) {
        BigDecimal totalPrice = BigDecimal.valueOf(00.00);
        for (int i = 0; i < service.length(); i++) {
            JSONObject services = service.getJSONObject(i);
            BigDecimal price = BigDecimal.valueOf(services.getDouble("price"));
            totalPrice = totalPrice.add(price);
        }
        return totalPrice;
    }

    @Override
    public BookingRepository getRepository() {

        return bookingRepository;
    }

    @Override
    public ViewBookingResponse viewBooking(Long id) {
        ViewBookingResponse response = new ViewBookingResponse();
        Optional<Booking> booking = Optional.ofNullable(bookingRepository.findById(id).orElseThrow(() -> new BookingNotFoundException("Booking Doesnt Exist")));
        mapper.map(booking, response);
        return response;
    }

    @Override
    public ViewBookingResponse findBookingByEmail(String email) {
        ViewBookingResponse response = new ViewBookingResponse();
        Optional<Booking> bookings = Optional.of(bookingRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("Service Doesnt Exist")));
        mapper.map(bookings, response);
        return response;
    }

    @Override
    public UpdateBookingResponse bookingDetailsUpdate(UpdateBookingRequest request) {
        Booking existingBooking = new Booking();
        UpdateBookingResponse updateResponse = new UpdateBookingResponse();

        if(bookingExist(request.getId())) {
            mapper.map(request, existingBooking);
            bookingRepository.save(existingBooking);
            String message = "Details Updated Successfully";
            mapper.map(message, updateResponse);
            return updateResponse;
        }
        else{

            throw new ServiceNotFoundException("Service Not Found");

        }
    }

    private boolean bookingExist(Long id){
        return !isEmpty(viewBooking(id));
    }

    @Override
    public void deleteBooking(Long id) {
        bookingRepository.deleteById(id);
    }

    @Override
    public void deleteBooking(String email) {
        bookingRepository.deleteByEmail(email);
    }
}
