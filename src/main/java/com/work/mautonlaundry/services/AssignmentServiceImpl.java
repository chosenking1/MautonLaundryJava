package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.BookingResource;
import com.work.mautonlaundry.data.model.DeliveryAssignment;
import com.work.mautonlaundry.data.model.LaundrymanAssignment;
import com.work.mautonlaundry.data.model.LocationTracking;
import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentStatus;
import com.work.mautonlaundry.data.model.enums.LaundrymanAssignmentStatus;
import com.work.mautonlaundry.data.repository.AddressRepository;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.BookingResourceRepository;
import com.work.mautonlaundry.data.repository.DeliveryAssignmentRepository;
import com.work.mautonlaundry.data.repository.LaundrymanAssignmentRepository;
import com.work.mautonlaundry.data.repository.LocationTrackingRepository;
import com.work.mautonlaundry.data.repository.RoleRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.UnauthorizedException;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.exceptions.serviceexceptions.ServiceNotFoundException;
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentServiceImpl implements AssignmentService {

    private static final int DELIVERY_OFFER_BATCH_SIZE = 3;
    private static final int DELIVERY_LOCATION_FRESHNESS_MINUTES = 30;
    private static final List<LaundrymanAssignmentStatus> ACTIVE_LAUNDRYMAN_STATUSES =
            List.of(LaundrymanAssignmentStatus.ASSIGNED, LaundrymanAssignmentStatus.RECEIVED);
    private static final List<DeliveryAssignmentStatus> ACTIVE_DELIVERY_STATUSES =
            List.of(DeliveryAssignmentStatus.OFFERED, DeliveryAssignmentStatus.ACCEPTED, 
                    DeliveryAssignmentStatus.ENROUTE_TO_CUSTOMER, DeliveryAssignmentStatus.ENROUTE_TO_LAUNDRY,
                    DeliveryAssignmentStatus.ENROUTE_FROM_LAUNDRY_TO_CUSTOMER);

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AddressRepository addressRepository;
    private final LaundrymanAssignmentRepository laundrymanAssignmentRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final BookingResourceRepository bookingResourceRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final GeoLocationService geoLocationService;
    private final NotificationService notificationService;
    @Value("${app.assignment.laundry.max-active-load:10}")
    private int maxLaundryActiveLoad;

    @Override
    @Transactional
    public void assignAgents(Booking booking) {
        booking.setStatus(BookingStatus.PENDING_ASSIGNMENT);
        boolean laundryAssigned = assignNearestLeastOccupiedLaundryman(booking);
        if (laundryAssigned) {
            booking.setStatus(BookingStatus.PENDING_LAUNDRYMAN_ACCEPTANCE);
            booking.setAssignmentTimestamp(LocalDateTime.now());
            booking.setAutoAccepted(false);
            bookingRepository.save(booking);
        }
    }

    @Override
    @Transactional
    public boolean acceptDeliveryOffer(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow(() -> new UnauthorizedException("User is not authenticated"));

        if (!currentUser.hasRole("DELIVERY_AGENT")) {
            throw new ForbiddenOperationException("Only delivery agents can accept delivery offers");
        }

        DeliveryAssignmentPhase phase = resolveAssignmentPhase(booking.getStatus());
        List<DeliveryAssignment> acceptedAssignments = deliveryAssignmentRepository
                .findByBookingAndPhaseAndStatus(booking, phase, DeliveryAssignmentStatus.ACCEPTED);
        if (!acceptedAssignments.isEmpty()) {
            return acceptedAssignments.stream().anyMatch(a -> a.getDeliveryAgent().getId().equals(currentUser.getId()));
        }

        DeliveryAssignment assignment = deliveryAssignmentRepository
                .findByBookingAndPhaseAndDeliveryAgent(booking, phase, currentUser)
                .orElseThrow(() -> new ServiceNotFoundException("No delivery offer found for this booking"));

        if (assignment.getStatus() != DeliveryAssignmentStatus.OFFERED) {
            return false;
        }

        assignment.setStatus(DeliveryAssignmentStatus.ACCEPTED);
        assignment.setRespondedAt(LocalDateTime.now());
        deliveryAssignmentRepository.save(assignment);

        List<DeliveryAssignment> openOffers = deliveryAssignmentRepository
                .findByBookingAndPhaseAndStatus(booking, phase, DeliveryAssignmentStatus.OFFERED);
        for (DeliveryAssignment offer : openOffers) {
            if (!offer.getDeliveryAgent().getId().equals(currentUser.getId())) {
                offer.setStatus(DeliveryAssignmentStatus.CANCELLED);
                offer.setRespondedAt(LocalDateTime.now());
                deliveryAssignmentRepository.save(offer);
            }
        }

        if (phase == DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER) {
            booking.setStatus(BookingStatus.ENROUTE_FOR_COLLECTION);
            BookingResource bookingResource = getOrCreateBookingResource(booking);
            bookingResource.setPickupAgentId(currentUser.getId());
            bookingResourceRepository.save(bookingResource);
            notificationService.notifyUserBookingStatusChange(
                    booking.getUser().getEmail(),
                    booking.getId(),
                    BookingStatus.LAUNDRYMAN_ACCEPTED.name(),
                    BookingStatus.ENROUTE_FOR_COLLECTION.name()
            );
        } else {
            booking.setStatus(BookingStatus.ENROUTE_TO_CUSTOMER);
            BookingResource bookingResource = getOrCreateBookingResource(booking);
            bookingResource.setReturnAgentId(currentUser.getId());
            bookingResourceRepository.save(bookingResource);
            notificationService.notifyUserBookingStatusChange(
                    booking.getUser().getEmail(),
                    booking.getId(),
                    BookingStatus.READY_FOR_PICKUP.name(),
                    BookingStatus.ENROUTE_TO_CUSTOMER.name()
            );
        }
        try {
            bookingRepository.save(booking);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ForbiddenOperationException("Booking was already claimed by another delivery agent");
        }
        return true;
    }

    @Override
    @Transactional
    public boolean declineDeliveryOffer(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalseForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow(() -> new UnauthorizedException("User is not authenticated"));

        if (!currentUser.hasRole("DELIVERY_AGENT")) {
            throw new ForbiddenOperationException("Only delivery agents can decline delivery offers");
        }

        DeliveryAssignmentPhase phase = resolveAssignmentPhase(booking.getStatus());
        DeliveryAssignment assignment = deliveryAssignmentRepository
                .findByBookingAndPhaseAndDeliveryAgent(booking, phase, currentUser)
                .orElseThrow(() -> new ServiceNotFoundException("No delivery offer found for this booking"));

        if (assignment.getStatus() != DeliveryAssignmentStatus.OFFERED) {
            return false;
        }

        assignment.setStatus(DeliveryAssignmentStatus.DECLINED);
        assignment.setRespondedAt(LocalDateTime.now());
        deliveryAssignmentRepository.save(assignment);

        List<DeliveryAssignment> remainingOffers = deliveryAssignmentRepository
                .findByBookingAndPhaseAndStatus(booking, phase, DeliveryAssignmentStatus.OFFERED);
        List<DeliveryAssignment> acceptedAssignments = deliveryAssignmentRepository
                .findByBookingAndPhaseAndStatus(booking, phase, DeliveryAssignmentStatus.ACCEPTED);

        if (remainingOffers.isEmpty() && acceptedAssignments.isEmpty()) {
            offerDeliveryAssignments(booking, phase);
        }
        return true;
    }

    @Override
    @Transactional
    public void notifyNearestDeliveryAgentsForDropOff(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        log.info("notifyNearestDeliveryAgentsForDropOff called for booking {}, status: {}", bookingId, booking.getStatus());

        if (laundrymanAssignmentRepository.findByBooking(booking).isEmpty()) {
            log.warn("No laundryman assignment found for booking {}, skipping delivery notification", bookingId);
            return;
        }
        DeliveryAssignmentPhase phase = resolveAssignmentPhase(booking.getStatus());
        log.info("Resolved phase {} for booking with status {}", phase, booking.getStatus());
        offerDeliveryAssignments(booking, phase);
    }

    @Override
    @Transactional
    public void acceptLaundryAssignment(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalseForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        LaundrymanAssignment assignment = resolveLaundryAssignmentForCurrentUser(booking);
        if (assignment.getStatus() != LaundrymanAssignmentStatus.ASSIGNED) {
            return;
        }
        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.LAUNDRYMAN_ACCEPTED);
        booking.setAutoAccepted(false);
        booking.setAssignmentTimestamp(LocalDateTime.now());
        bookingRepository.save(booking);

        notificationService.notifyUserBookingStatusChange(
                booking.getUser().getEmail(),
                booking.getId(),
                oldStatus.name(),
                BookingStatus.LAUNDRYMAN_ACCEPTED.name()
        );

        notifyNearestDeliveryAgentsForDropOff(bookingId);
    }

    @Override
    @Transactional
    public void assignLaundryAgentByAdmin(String bookingId, String laundryAgentId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalseForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        AppUser laundryAgent = userRepository.findUserById(laundryAgentId)
                .orElseThrow(() -> new UserNotFoundException("Laundry agent not found"));

        if (!laundryAgent.hasRole("LAUNDRY_AGENT")) {
            throw new ForbiddenOperationException("Selected user is not a laundry agent");
        }

        LaundrymanAssignment assignment = laundrymanAssignmentRepository.findByBooking(booking)
                .orElseGet(LaundrymanAssignment::new);
        assignment.setBooking(booking);
        assignment.setLaundryman(laundryAgent);
        assignment.setStatus(LaundrymanAssignmentStatus.ASSIGNED);
        laundrymanAssignmentRepository.save(assignment);
        booking.setStatus(BookingStatus.PENDING_LAUNDRYMAN_ACCEPTANCE);
        booking.setAssignmentTimestamp(LocalDateTime.now());
        booking.setAutoAccepted(false);
        bookingRepository.save(booking);

        BookingResource bookingResource = getOrCreateBookingResource(booking);
        bookingResource.setLaundryAgentId(laundryAgent.getId());
        bookingResourceRepository.save(bookingResource);

        List<DeliveryAssignment> openPickupOffers = deliveryAssignmentRepository.findByBookingAndPhaseAndStatus(
                booking,
                DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER,
                DeliveryAssignmentStatus.OFFERED
        );
        List<DeliveryAssignment> acceptedPickupAssignments = deliveryAssignmentRepository.findByBookingAndPhaseAndStatus(
                booking,
                DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER,
                DeliveryAssignmentStatus.ACCEPTED
        );

        if (openPickupOffers.isEmpty() && acceptedPickupAssignments.isEmpty()) {
            // Wait for laundry acceptance (or TTL auto-accept) before pickup offers.
        }
    }

    @Override
    @Transactional
    public void markLaundryReceived(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalseForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        LaundrymanAssignment assignment = resolveLaundryAssignmentForCurrentUser(booking);
        assignment.setStatus(LaundrymanAssignmentStatus.RECEIVED);
        laundrymanAssignmentRepository.save(assignment);
        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.RECEIVED_BY_LAUNDRY);
        bookingRepository.save(booking);

        notificationService.notifyUserBookingStatusChange(
                booking.getUser().getEmail(),
                booking.getId(),
                oldStatus.name(),
                BookingStatus.RECEIVED_BY_LAUNDRY.name()
        );
    }

    @Override
    @Transactional
    public void markLaundryCompleted(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalseForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        LaundrymanAssignment assignment = resolveLaundryAssignmentForCurrentUser(booking);
        assignment.setStatus(LaundrymanAssignmentStatus.COMPLETED);
        laundrymanAssignmentRepository.save(assignment);
        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.READY_FOR_PICKUP);
        LocalDateTime deliveryBroadcastAt = resolveDefaultDeliveryBroadcastTime(booking);
        booking.setDeliveryBroadcastAt(deliveryBroadcastAt);
        booking.setCustomerEarlyDeliveryOptIn(null);
        booking.setCustomerEarlyDeliveryDecisionAt(null);
        bookingRepository.save(booking);

        notificationService.notifyUserBookingStatusChange(
                booking.getUser().getEmail(),
                booking.getId(),
                oldStatus.name(),
                BookingStatus.READY_FOR_PICKUP.name()
        );

        // Broadcast delivery offers as soon as laundry is marked ready for pickup.
        // offerDeliveryAssignments() is idempotent for open/accepted assignments, so this is safe.
        notifyNearestDeliveryAgentsForDropOff(bookingId);
        booking.setDeliveryBroadcastAt(null);
        bookingRepository.save(booking);

        if (isEarlyCompletion(booking)) {
            notificationService.notifyEarlyDeliveryOption(
                    booking.getUser().getEmail(),
                    booking.getId(),
                    LocalDate.now().plusDays(1).toString()
            );
        }
    }

    private boolean assignNearestLeastOccupiedLaundryman(Booking booking) {
        Optional<LaundrymanAssignment> existing = laundrymanAssignmentRepository.findByBooking(booking);
        if (existing.isPresent()) {
            return true;
        }

        Address pickupAddress = booking.getPickupAddress();
        if (!hasCoordinates(pickupAddress)) {
            return false;
        }

        Role laundryRole = roleRepository.findByName("LAUNDRY_AGENT")
                .orElseThrow(() -> new ServiceNotFoundException("LAUNDRY_AGENT role not found"));

        List<AppUser> laundryAgents = userRepository.findByRoleAndDeletedFalse(laundryRole);
        Optional<CandidateLaundryman> selected = laundryAgents.stream()
                .filter(agent -> Boolean.TRUE.equals(agent.getOnline()))
                .filter(agent -> agent.getRating() == null || agent.getRating() >= 3.5)
                .map(agent -> toLaundrymanCandidate(agent, pickupAddress))
                .flatMap(Optional::stream)
                .min(Comparator.comparingLong(CandidateLaundryman::activeAssignments)
                        .thenComparingDouble(CandidateLaundryman::distanceKm));

        if (selected.isEmpty()) {
            return false;
        }

        LaundrymanAssignment assignment = new LaundrymanAssignment();
        assignment.setBooking(booking);
        assignment.setLaundryman(selected.get().agent());
        assignment.setStatus(LaundrymanAssignmentStatus.ASSIGNED);
        laundrymanAssignmentRepository.save(assignment);

        BookingResource bookingResource = getOrCreateBookingResource(booking);
        bookingResource.setLaundryAgentId(selected.get().agent().getId());
        bookingResourceRepository.save(bookingResource);

        notificationService.notifyLaundrymanAssigned(
                selected.get().agent().getEmail(),
                booking.getId(),
                selected.get().activeAssignments()
        );

        notificationService.notifyUserLaundrymanAssigned(
                booking.getUser().getEmail(),
                booking.getId(),
                selected.get().agent().getFull_name()
        );
        return true;
    }

    private Optional<CandidateLaundryman> toLaundrymanCandidate(AppUser agent, Address pickupAddress) {
        Optional<Address> laundryAddress = findBestAddress(agent);
        if (laundryAddress.isEmpty() || !hasCoordinates(laundryAddress.get())) {
            return Optional.empty();
        }

        long activeAssignments = laundrymanAssignmentRepository
                .countByLaundrymanAndStatusIn(agent, ACTIVE_LAUNDRYMAN_STATUSES);
        if (activeAssignments >= maxLaundryActiveLoad) {
            return Optional.empty();
        }
        double distance = geoLocationService.calculateDistance(
                pickupAddress.getLatitude(),
                pickupAddress.getLongitude(),
                laundryAddress.get().getLatitude(),
                laundryAddress.get().getLongitude()
        );
        return Optional.of(new CandidateLaundryman(agent, activeAssignments, distance));
    }

    private void offerDeliveryAssignments(Booking booking, DeliveryAssignmentPhase phase) {
        List<DeliveryAssignment> phaseAssignments = deliveryAssignmentRepository.findByBookingAndPhase(booking, phase);
        boolean hasOpenOrAccepted = phaseAssignments.stream().anyMatch(assignment ->
                assignment.getStatus() == DeliveryAssignmentStatus.OFFERED
                        || assignment.getStatus() == DeliveryAssignmentStatus.ACCEPTED
                        || assignment.getStatus() == DeliveryAssignmentStatus.ENROUTE_TO_CUSTOMER
                        || assignment.getStatus() == DeliveryAssignmentStatus.ENROUTE_TO_LAUNDRY
                        || assignment.getStatus() == DeliveryAssignmentStatus.ENROUTE_FROM_LAUNDRY_TO_CUSTOMER
        );

        if (hasOpenOrAccepted) {
            log.debug("Delivery assignments already exist for booking {} phase {}, skipping", booking.getId(), phase);
            return;
        }

        Set<String> excludedAgentIds = new HashSet<>();
        phaseAssignments.forEach(assignment -> excludedAgentIds.add(assignment.getDeliveryAgent().getId()));

        Address originAddress = resolveDeliveryOriginAddress(booking, phase);
        log.debug("Origin address for booking {} phase {}: {}", booking.getId(), phase, originAddress);
        if (!hasCoordinates(originAddress)) {
            log.warn("Cannot offer delivery assignments: origin address has no coordinates for booking {}", booking.getId());
            return;
        }

        Role deliveryRole = roleRepository.findByName("DELIVERY_AGENT")
                .orElseThrow(() -> new ServiceNotFoundException("DELIVERY_AGENT role not found"));

        List<AppUser> deliveryAgents = userRepository.findByRoleAndDeletedFalse(deliveryRole);
        log.debug("Found {} delivery agents for booking {}", deliveryAgents.size(), booking.getId());
        
        List<CandidateDeliveryman> nearestCandidates = deliveryAgents.stream()
                .filter(agent -> !excludedAgentIds.contains(agent.getId()))
                .map(agent -> toDeliveryCandidate(agent, originAddress))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingDouble(CandidateDeliveryman::distanceKm)
                        .thenComparingLong(CandidateDeliveryman::activeAssignments))
                .limit(DELIVERY_OFFER_BATCH_SIZE)
                .toList();
        
        log.debug("Found {} valid delivery candidates for booking {}", nearestCandidates.size(), booking.getId());

        for (CandidateDeliveryman candidate : nearestCandidates) {
            DeliveryAssignment assignment = new DeliveryAssignment();
            assignment.setBooking(booking);
            assignment.setDeliveryAgent(candidate.agent());
            assignment.setPhase(phase);
            assignment.setStatus(DeliveryAssignmentStatus.OFFERED);
            deliveryAssignmentRepository.save(assignment);

            notificationService.notifyDeliveryOffer(
                    candidate.agent().getEmail(),
                    booking.getId(),
                    phase.name(),
                    candidate.distanceKm()
            );
        }
    }

    private Optional<CandidateDeliveryman> toDeliveryCandidate(AppUser deliveryAgent, Address originAddress) {
        Double agentLat = null;
        Double agentLon = null;
        
        LocalDateTime freshnessThreshold = LocalDateTime.now().minusMinutes(DELIVERY_LOCATION_FRESHNESS_MINUTES);
        Optional<LocationTracking> recentLocation = locationTrackingRepository.findLatestByUserSince(deliveryAgent, freshnessThreshold);
        
        if (recentLocation.isPresent()) {
            agentLat = recentLocation.get().getLatitude().doubleValue();
            agentLon = recentLocation.get().getLongitude().doubleValue();
            log.debug("Using recent location for delivery agent {}: {}, {}", deliveryAgent.getEmail(), agentLat, agentLon);
        } else {
            Optional<Address> agentAddress = findBestAddress(deliveryAgent);
            log.debug("No recent location for delivery agent {}, checking saved address: {}", deliveryAgent.getEmail(), agentAddress);
            if (agentAddress.isEmpty() || !hasCoordinates(agentAddress.get())) {
                log.warn("Delivery agent {} has no valid coordinates (no recent location and no saved address with lat/long)", deliveryAgent.getEmail());
                return Optional.empty();
            }
            agentLat = agentAddress.get().getLatitude().doubleValue();
            agentLon = agentAddress.get().getLongitude().doubleValue();
        }

        long activeAssignments = deliveryAssignmentRepository
                .countByDeliveryAgentAndStatusIn(deliveryAgent, ACTIVE_DELIVERY_STATUSES);
        double distance = geoLocationService.calculateDistance(
                originAddress.getLatitude(),
                originAddress.getLongitude(),
                agentLat,
                agentLon
        );
        return Optional.of(new CandidateDeliveryman(deliveryAgent, activeAssignments, distance));
    }

    private Address resolveDeliveryOriginAddress(Booking booking, DeliveryAssignmentPhase phase) {
        if (phase == DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER) {
            return booking.getPickupAddress();
        }

        Optional<LaundrymanAssignment> laundryAssignment = laundrymanAssignmentRepository.findByBooking(booking);
        if (laundryAssignment.isEmpty()) {
            return booking.getPickupAddress();
        }

        return findBestAddress(laundryAssignment.get().getLaundryman()).orElse(booking.getPickupAddress());
    }

    private Optional<Address> findBestAddress(AppUser user) {
        List<Address> addresses = addressRepository.findByUserAndDeletedFalse(user);
        log.debug("Found {} addresses for user {} (excluding deleted)", addresses.size(), user.getEmail());
        List<Address> withCoords = addresses.stream().filter(this::hasCoordinates).toList();
        log.debug("Found {} addresses with coordinates for user {}", withCoords.size(), user.getEmail());
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

    private DeliveryAssignmentPhase resolveAssignmentPhase(BookingStatus status) {
        if (status == BookingStatus.READY_FOR_PICKUP
                || status == BookingStatus.ENROUTE_TO_CUSTOMER
                || status == BookingStatus.READY_FOR_DELIVERY
                || status == BookingStatus.OUT_FOR_DELIVERY) {
            return DeliveryAssignmentPhase.DELIVER_TO_CUSTOMER;
        }
        return DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER;
    }

    private record CandidateLaundryman(AppUser agent, long activeAssignments, double distanceKm) {
    }

    private record CandidateDeliveryman(AppUser agent, long activeAssignments, double distanceKm) {
    }

    private BookingResource getOrCreateBookingResource(Booking booking) {
        return bookingResourceRepository.findByBooking_Id(booking.getId()).orElseGet(() -> {
            BookingResource bookingResource = new BookingResource();
            bookingResource.setBooking(booking);
            return bookingResource;
        });
    }

    private LocalDateTime resolveDefaultDeliveryBroadcastTime(Booking booking) {
        LocalDateTime now = LocalDateTime.now();
        if (booking.getReturnDate() == null) {
            return now;
        }
        return booking.getReturnDate().isAfter(now) ? booking.getReturnDate() : now;
    }

    private boolean isEarlyCompletion(Booking booking) {
        if (booking.getReturnDate() == null) {
            return false;
        }
        return LocalDate.now().plusDays(1).isBefore(booking.getReturnDate().toLocalDate());
    }

    private LaundrymanAssignment resolveLaundryAssignmentForCurrentUser(Booking booking) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow(() -> new UnauthorizedException("User is not authenticated"));
        LaundrymanAssignment assignment = laundrymanAssignmentRepository.findByBooking(booking)
                .orElseThrow(() -> new ServiceNotFoundException("Laundry assignment not found"));

        boolean isAdmin = currentUser.hasRole("ADMIN");
        boolean isAssignedLaundryman = assignment.getLaundryman() != null
                && assignment.getLaundryman().getId().equals(currentUser.getId());
        if (!isAdmin && !isAssignedLaundryman) {
            throw new ForbiddenOperationException("Only assigned laundry agent can update this booking");
        }
        return assignment;
    }
}
