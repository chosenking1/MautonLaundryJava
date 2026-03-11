package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Booking;

public interface AssignmentService {
    void assignAgents(Booking booking);
    boolean acceptDeliveryOffer(String bookingId);
    boolean declineDeliveryOffer(String bookingId);
    void acceptLaundryAssignment(String bookingId);
    void notifyNearestDeliveryAgentsForDropOff(String bookingId);
    void assignLaundryAgentByAdmin(String bookingId, String laundryAgentId);
    void markLaundryReceived(String bookingId);
    void markLaundryCompleted(String bookingId);
}
