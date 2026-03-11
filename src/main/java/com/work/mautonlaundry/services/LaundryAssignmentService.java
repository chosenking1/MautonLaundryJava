package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.LaundrymanAssignment;
import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase;
import com.work.mautonlaundry.data.model.enums.LaundrymanAssignmentStatus;
import com.work.mautonlaundry.data.repository.AddressRepository;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.LaundrymanAssignmentRepository;
import com.work.mautonlaundry.data.repository.RoleRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.exceptions.serviceexceptions.ServiceNotFoundException;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LaundryAssignmentService {
    private static final int DEFAULT_OFFER_BATCH_SIZE = 3;

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AddressRepository addressRepository;
    private final LaundrymanAssignmentRepository laundrymanAssignmentRepository;
    private final GeoLocationService geoLocationService;
    private final NotificationService notificationService;
    private final BookingStateMachine bookingStateMachine;
    private final DispatchEngine dispatchEngine;

    @Value("${app.assignment.laundry.offer-batch-size:3}")
    private int offerBatchSize;

    @Transactional
    public void createLaundryOffers(Booking booking) {
        if (booking.getStatus() == BookingStatus.LAUNDRY_ACCEPTED) {
            return;
        }
        List<LaundrymanAssignment> existing = laundrymanAssignmentRepository.findByBooking(booking);
        boolean hasActiveOffer = existing.stream().anyMatch(assignment ->
                assignment.getStatus() == LaundrymanAssignmentStatus.OFFERED
                        || assignment.getStatus() == LaundrymanAssignmentStatus.ACCEPTED
                        || assignment.getStatus() == LaundrymanAssignmentStatus.AUTO_ACCEPTED
        );
        if (hasActiveOffer) {
            return;
        }
        bookingStateMachine.validateTransition(booking.getStatus(), BookingStatus.LAUNDRY_ASSIGNMENT_PENDING);
        booking.setStatus(BookingStatus.LAUNDRY_ASSIGNMENT_PENDING);
        booking.setAssignmentTimestamp(LocalDateTime.now());
        bookingRepository.save(booking);

        Address pickupAddress = booking.getPickupAddress();
        if (!hasCoordinates(pickupAddress)) {
            throw new ForbiddenOperationException("Pickup address has no coordinates");
        }

        Role laundryRole = roleRepository.findByName("LAUNDRY_AGENT")
                .orElseThrow(() -> new ServiceNotFoundException("LAUNDRY_AGENT role not found"));

        List<AppUser> laundryAgents = userRepository.findByRoleAndDeletedFalse(laundryRole);
        int batchSize = offerBatchSize > 0 ? offerBatchSize : DEFAULT_OFFER_BATCH_SIZE;
        List<AppUser> nearest = laundryAgents.stream()
                .filter(agent -> Boolean.TRUE.equals(agent.getOnline()))
                .map(agent -> toLaundryCandidate(agent, pickupAddress))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingDouble(CandidateLaundry::distanceKm))
                .limit(batchSize)
                .map(CandidateLaundry::agent)
                .toList();

        for (AppUser agent : nearest) {
            LaundrymanAssignment assignment = new LaundrymanAssignment();
            assignment.setBooking(booking);
            assignment.setLaundryman(agent);
            assignment.setStatus(LaundrymanAssignmentStatus.OFFERED);
            laundrymanAssignmentRepository.save(assignment);
            notificationService.notifyLaundryOffer(agent.getEmail(), booking.getId());
        }
    }

    @Transactional
    public void assignLaundryAgentByAdmin(String bookingId, String laundryAgentId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        AppUser laundryAgent = userRepository.findById(laundryAgentId)
                .orElseThrow(() -> new ServiceNotFoundException("Laundry agent not found"));
        if (Boolean.TRUE.equals(laundryAgent.getDeleted()) || !laundryAgent.hasRole("LAUNDRY_AGENT")) {
            throw new ForbiddenOperationException("User is not a valid laundry agent");
        }

        Optional<LaundrymanAssignment> existingAccepted = laundrymanAssignmentRepository
                .findFirstByBookingAndStatusInOrderByCreatedAtDesc(
                        booking,
                        List.of(LaundrymanAssignmentStatus.ACCEPTED, LaundrymanAssignmentStatus.AUTO_ACCEPTED)
                );
        if (existingAccepted.isPresent()) {
            if (existingAccepted.get().getLaundryman().getId().equals(laundryAgentId)) {
                return;
            }
            throw new ForbiddenOperationException("Booking already assigned to a laundry agent");
        }

        if (booking.getStatus() == BookingStatus.CREATED) {
            bookingStateMachine.validateTransition(booking.getStatus(), BookingStatus.LAUNDRY_ASSIGNMENT_PENDING);
            booking.setStatus(BookingStatus.LAUNDRY_ASSIGNMENT_PENDING);
            booking.setAssignmentTimestamp(LocalDateTime.now());
            bookingRepository.save(booking);
        } else if (booking.getStatus() != BookingStatus.LAUNDRY_ASSIGNMENT_PENDING) {
            throw new ForbiddenOperationException("Booking is not eligible for laundry assignment");
        }

        LaundrymanAssignment assignment = laundrymanAssignmentRepository.findByBookingAndLaundryman(booking, laundryAgent)
                .orElseGet(() -> {
                    LaundrymanAssignment created = new LaundrymanAssignment();
                    created.setBooking(booking);
                    created.setLaundryman(laundryAgent);
                    return created;
                });
        assignment.setStatus(LaundrymanAssignmentStatus.ACCEPTED);
        laundrymanAssignmentRepository.save(assignment);

        expireOtherLaundryOffers(booking, laundryAgent.getId());
        moveBookingToLaundryAccepted(booking, LaundrymanAssignmentStatus.ACCEPTED);
    }

    @Transactional
    public void acceptLaundryOffer(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();

        LaundrymanAssignment assignment = laundrymanAssignmentRepository.findByBookingAndLaundryman(booking, currentUser)
                .orElseThrow(() -> new ForbiddenOperationException("No laundry offer found for this booking"));

        if (assignment.getStatus() != LaundrymanAssignmentStatus.OFFERED) {
            return;
        }

        assignment.setStatus(LaundrymanAssignmentStatus.ACCEPTED);
        laundrymanAssignmentRepository.save(assignment);

        expireOtherLaundryOffers(booking, currentUser.getId());
        moveBookingToLaundryAccepted(booking, LaundrymanAssignmentStatus.ACCEPTED);
    }

    @Transactional
    public void rejectLaundryOffer(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();

        LaundrymanAssignment assignment = laundrymanAssignmentRepository.findByBookingAndLaundryman(booking, currentUser)
                .orElseThrow(() -> new ForbiddenOperationException("No laundry offer found for this booking"));

        if (assignment.getStatus() != LaundrymanAssignmentStatus.OFFERED) {
            return;
        }

        assignment.setStatus(LaundrymanAssignmentStatus.REJECTED);
        laundrymanAssignmentRepository.save(assignment);
    }

    @Transactional
    public void autoAcceptExpiredOffer(LaundrymanAssignment offer) {
        Booking booking = offer.getBooking();
        if (booking.getStatus() != BookingStatus.LAUNDRY_ASSIGNMENT_PENDING) {
            return;
        }

        offer.setStatus(LaundrymanAssignmentStatus.AUTO_ACCEPTED);
        laundrymanAssignmentRepository.save(offer);

        expireOtherLaundryOffers(booking, offer.getLaundryman().getId());
        moveBookingToLaundryAccepted(booking, LaundrymanAssignmentStatus.AUTO_ACCEPTED);
    }

    private void moveBookingToLaundryAccepted(Booking booking, LaundrymanAssignmentStatus acceptedStatus) {
        bookingStateMachine.validateTransition(booking.getStatus(), BookingStatus.LAUNDRY_ACCEPTED);
        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.LAUNDRY_ACCEPTED);
        bookingRepository.save(booking);
        notificationService.notifyUserBookingStatusChange(
                booking.getUser().getEmail(),
                booking.getId(),
                oldStatus.name(),
                BookingStatus.LAUNDRY_ACCEPTED.name()
        );

        bookingStateMachine.validateTransition(booking.getStatus(), BookingStatus.PICKUP_DISPATCH_PENDING);
        booking.setStatus(BookingStatus.PICKUP_DISPATCH_PENDING);
        bookingRepository.save(booking);
        dispatchEngine.enqueueDispatchJob(booking, DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER);
        notificationService.notifyUserBookingStatusChange(
                booking.getUser().getEmail(),
                booking.getId(),
                BookingStatus.LAUNDRY_ACCEPTED.name(),
                BookingStatus.PICKUP_DISPATCH_PENDING.name()
        );

        log.info("Laundry assignment {} for booking {} accepted: {}", acceptedStatus, booking.getId(), booking.getStatus());
    }

    private void expireOtherLaundryOffers(Booking booking, String acceptedLaundryId) {
        List<LaundrymanAssignment> offers = laundrymanAssignmentRepository.findByBooking(booking);
        for (LaundrymanAssignment offer : offers) {
            if (!offer.getLaundryman().getId().equals(acceptedLaundryId)
                    && offer.getStatus() == LaundrymanAssignmentStatus.OFFERED) {
                offer.setStatus(LaundrymanAssignmentStatus.EXPIRED);
                laundrymanAssignmentRepository.save(offer);
            }
        }
    }

    private Optional<CandidateLaundry> toLaundryCandidate(AppUser agent, Address pickupAddress) {
        Optional<Address> laundryAddress = findBestAddress(agent);
        if (laundryAddress.isEmpty() || !hasCoordinates(laundryAddress.get())) {
            return Optional.empty();
        }
        double distance = geoLocationService.calculateDistance(
                pickupAddress.getLatitude(),
                pickupAddress.getLongitude(),
                laundryAddress.get().getLatitude(),
                laundryAddress.get().getLongitude()
        );
        return Optional.of(new CandidateLaundry(agent, distance));
    }

    private Optional<Address> findBestAddress(AppUser user) {
        List<Address> addresses = addressRepository.findByUserAndDeletedFalse(user);
        return addresses.stream()
                .filter(this::hasCoordinates)
                .sorted((a, b) -> {
                    boolean aDefault = Boolean.TRUE.equals(a.getIsDefault());
                    boolean bDefault = Boolean.TRUE.equals(b.getIsDefault());
                    if (aDefault != bDefault) {
                        return bDefault ? 1 : -1;
                    }
                    LocalDateTime aLastUsed = a.getLastUsed() == null ? LocalDateTime.MIN : a.getLastUsed();
                    LocalDateTime bLastUsed = b.getLastUsed() == null ? LocalDateTime.MIN : b.getLastUsed();
                    return bLastUsed.compareTo(aLastUsed);
                })
                .findFirst();
    }

    private boolean hasCoordinates(Address address) {
        return address != null && address.getLatitude() != null && address.getLongitude() != null;
    }

    private record CandidateLaundry(AppUser agent, double distanceKm) {
    }
}
