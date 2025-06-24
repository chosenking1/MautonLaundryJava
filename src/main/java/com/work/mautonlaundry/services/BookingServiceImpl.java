package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.*;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.requests.bookingrequests.RegisterBookingRequest;
import com.work.mautonlaundry.dtos.requests.bookingrequests.UpdateBookingRequest;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.DeliveryRequest;
import com.work.mautonlaundry.dtos.requests.paymentrequests.CreatePaymentRequest;
import com.work.mautonlaundry.dtos.requests.servicerequests.ServiceRequestDetail;
import com.work.mautonlaundry.dtos.responses.bookingresponse.RegisterBookingResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.UpdateBookingResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.ViewBookingResponse;
import com.work.mautonlaundry.exceptions.addressexception.AddressNotFoundException;
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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.util.ObjectUtils.isEmpty;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService{
    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    PaymentService  payment;

    @Autowired
    DeliveryManagementService deliveryManagementService;
    ModelMapper mapper = new ModelMapper();

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    private final ServiceRepository serviceRepository;
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
        JSONArray services = new JSONArray();

        for (ServiceRequestDetail detail : request.getServiceDetails()) {
            JSONObject obj = new JSONObject();
            obj.put("serviceId", detail.getServiceId());
            obj.put("isWhite", detail.isWhite());
            services.put(obj);
        }

        booking.setService(services.toString());

        // Save booking
        Booking savedBooking = bookingRepository.save(booking);

        // Create payment record
        createPayment(savedBooking);


        // Setup delivery
        createDeliveryManagement(request.getAddressId(), savedBooking);

        return mapper.map(savedBooking, RegisterBookingResponse.class);
    }

    private void createPayment(Booking booking) {

        CreatePaymentRequest createPaymentRequest = new CreatePaymentRequest();

        createPaymentRequest.setBookingId(booking.getId());
        createPaymentRequest.setAmount(totalPriceCalculation(booking));

        payment.createPayment(createPaymentRequest);

    }

    private void createDeliveryManagement(String addressId, Booking booking) {
        DeliveryManagement delivery = new DeliveryManagement();

        DeliveryRequest request = new DeliveryRequest();
        request.setBooking_id(booking.getId());
        request.setUserAddress(addressId);
        request.setDate_booked(booking.getDate_booked());
        request.setUrgency(booking.getUrgency());

        deliveryManagementService.createDeliveryDetails(request);

    }

    private BigDecimal totalPriceCalculation(Booking booking) {
        BigDecimal totalPrice = BigDecimal.ZERO;

        List<ServiceRequestDetail> serviceRequestDetails = parseServiceDetails(booking.getService());

        // Step 1: Collect service IDs
        List<Long> serviceIds = serviceRequestDetails.stream()
                .map(ServiceRequestDetail::getServiceId)
                .distinct()
                .toList();

        // Step 2: Fetch all services in one DB call
        List<Services> servicesList = serviceRepository.findAllById(serviceIds);

        // Step 3: Map serviceId -> Services for fast lookup
        Map<Long, Services> serviceMap = servicesList.stream()
                .collect(Collectors.toMap(Services::getId, s -> s));

        // Step 4: Calculate total using the map
        for (ServiceRequestDetail detail : serviceRequestDetails) {
            Services service = serviceMap.get(detail.getServiceId());

            if (service == null) {
                throw new IllegalArgumentException("Service ID not found: " + detail.getServiceId());
            }

            ServicePrice price = service.getServicePrice();
            BigDecimal basePrice = BigDecimal.valueOf(price.getPrice());

            if (detail.isWhite()) {
                basePrice = basePrice.add(BigDecimal.valueOf(price.getWhite()));
            }

            totalPrice = totalPrice.add(basePrice);
        }

        // Step 5: Apply express multiplier if needed
        if (booking.getUrgency() == UrgencyType.EXPRESS) {
            totalPrice = totalPrice.multiply(BigDecimal.valueOf(1.5));
        }

        return totalPrice.setScale(2, RoundingMode.HALF_UP);
    }


    private List<ServiceRequestDetail> parseServiceDetails(String servicesJson) {
        JSONArray jsonArray = new JSONArray(servicesJson);
        List<ServiceRequestDetail> items = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            ServiceRequestDetail item = new ServiceRequestDetail();
            item.setServiceId(obj.getLong("serviceId"));
            item.setWhite(obj.getBoolean("isWhite"));

            items.add(item);
        }

        return items;
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
