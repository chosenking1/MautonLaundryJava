package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.model.AgentApplication;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.enums.AgentApplicationStatus;
import com.work.mautonlaundry.data.model.enums.AgentApplicationType;
import com.work.mautonlaundry.data.model.enums.InspectionStatus;
import com.work.mautonlaundry.data.repository.AddressRepository;
import com.work.mautonlaundry.data.repository.AgentApplicationRepository;
import com.work.mautonlaundry.exceptions.ConflictException;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentApplicationService {
    private final AgentApplicationRepository agentApplicationRepository;
    private final AddressRepository addressRepository;
    private final NotificationService notificationService;
    private final RoleChangeService roleChangeService;

    @Value("${app.admin.team-email:}")
    private String adminTeamEmail;

    @Transactional
    public AgentApplication submitLaundryApplication(List<String> addressIds) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow(() -> new UserNotFoundException("User not found"));
        ensureUserEligible(currentUser);
        ensureNoActiveApplication(currentUser, AgentApplicationType.LAUNDRY_AGENT);

        List<Address> addresses = fetchAddresses(currentUser, addressIds);
        validateAddressesHaveCoordinates(addresses);

        AgentApplication application = new AgentApplication();
        application.setUser(currentUser);
        application.setType(AgentApplicationType.LAUNDRY_AGENT);
        application.setStatus(AgentApplicationStatus.SUBMITTED);
        application.setInspectionStatus(InspectionStatus.PENDING);
        application.getAddresses().addAll(addresses);

        AgentApplication saved = agentApplicationRepository.save(application);

        notificationService.notifyAgentApplicationSubmitted(
                currentUser.getEmail(),
                "LAUNDRY_AGENT",
                addresses.size(),
                adminTeamEmail
        );
        notificationService.notifyAdminNewAgentApplication(
                adminTeamEmail,
                currentUser.getEmail(),
                "LAUNDRY_AGENT",
                addresses.size()
        );

        return saved;
    }

    @Transactional
    public AgentApplication submitDeliveryApplication(String addressId) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow(() -> new UserNotFoundException("User not found"));
        ensureUserEligible(currentUser);
        ensureNoActiveApplication(currentUser, AgentApplicationType.DELIVERY_AGENT);

        List<Address> addresses = fetchAddresses(currentUser, List.of(addressId));
        validateAddressesHaveCoordinates(addresses);

        AgentApplication application = new AgentApplication();
        application.setUser(currentUser);
        application.setType(AgentApplicationType.DELIVERY_AGENT);
        application.setStatus(AgentApplicationStatus.SUBMITTED);
        application.setInspectionStatus(InspectionStatus.NOT_REQUIRED);
        application.getAddresses().addAll(addresses);

        AgentApplication saved = agentApplicationRepository.save(application);

        notificationService.notifyAgentApplicationSubmitted(
                currentUser.getEmail(),
                "DELIVERY_AGENT",
                addresses.size(),
                adminTeamEmail
        );
        notificationService.notifyAdminNewAgentApplication(
                adminTeamEmail,
                currentUser.getEmail(),
                "DELIVERY_AGENT",
                addresses.size()
        );

        return saved;
    }

    @Transactional
    public AgentApplication inspectLaundryApplication(Long applicationId, boolean passed, List<String> failedAddressIds, String notes, String rejectionReason) {
        AgentApplication application = getApplication(applicationId);
        if (application.getType() != AgentApplicationType.LAUNDRY_AGENT) {
            throw new ForbiddenOperationException("Only laundry applications require inspection");
        }
        if (application.getStatus() != AgentApplicationStatus.SUBMITTED) {
            throw new ConflictException("Application is not in a reviewable state");
        }

        AppUser admin = SecurityUtil.getCurrentUser().orElseThrow();
        application.setInspectedByAdminId(admin.getId());
        application.setInspectionNotes(notes);

        if (failedAddressIds != null && !failedAddressIds.isEmpty()) {
            Set<String> allowed = application.getAddresses().stream()
                    .map(Address::getId)
                    .collect(Collectors.toSet());
            boolean invalid = failedAddressIds.stream().anyMatch(id -> !allowed.contains(id));
            if (invalid) {
                throw new ForbiddenOperationException("Failed address IDs must belong to the application");
            }
        }

        if (!passed) {
            String reason = rejectionReason == null || rejectionReason.isBlank()
                    ? "Inspection failed"
                    : rejectionReason.trim();
            application.setInspectionStatus(InspectionStatus.FAILED);
            application.setStatus(AgentApplicationStatus.REJECTED);
            application.setRejectionReason(reason);
            AgentApplication saved = agentApplicationRepository.save(application);
            notificationService.notifyAgentApplicationRejected(
                    application.getUser().getEmail(),
                    application.getType().name(),
                    reason,
                    adminTeamEmail
            );
            return saved;
        }

        if (failedAddressIds != null && !failedAddressIds.isEmpty()) {
            String reason = rejectionReason == null || rejectionReason.isBlank()
                    ? "One or more inspection locations failed"
                    : rejectionReason.trim();
            application.setInspectionStatus(InspectionStatus.FAILED);
            application.setStatus(AgentApplicationStatus.REJECTED);
            application.setRejectionReason(reason);
            AgentApplication saved = agentApplicationRepository.save(application);
            notificationService.notifyAgentApplicationRejected(
                    application.getUser().getEmail(),
                    application.getType().name(),
                    reason,
                    adminTeamEmail
            );
            return saved;
        }

        application.setInspectionStatus(InspectionStatus.PASSED);
        return agentApplicationRepository.save(application);
    }

    @Transactional
    public AgentApplication rejectApplication(Long applicationId, String reason) {
        AgentApplication application = getApplication(applicationId);
        if (application.getStatus() == AgentApplicationStatus.APPROVED) {
            throw new ConflictException("Approved application cannot be rejected");
        }
        if (application.getStatus() == AgentApplicationStatus.REJECTED) {
            return application;
        }
        String rejectionReason = reason == null || reason.isBlank() ? "Application rejected" : reason.trim();
        application.setStatus(AgentApplicationStatus.REJECTED);
        application.setInspectionStatus(InspectionStatus.FAILED);
        application.setRejectionReason(rejectionReason);
        AgentApplication saved = agentApplicationRepository.save(application);
        notificationService.notifyAgentApplicationRejected(
                application.getUser().getEmail(),
                application.getType().name(),
                rejectionReason,
                adminTeamEmail
        );
        return saved;
    }

    @Transactional
    public void createRoleChangeRequest(Long applicationId) {
        roleChangeService.createRequestForApplication(applicationId);
    }

    public List<AgentApplication> getApplications(AgentApplicationStatus status, AgentApplicationType type) {
        if (status != null && type != null) {
            return agentApplicationRepository.findByStatusAndType(status, type);
        }
        if (status != null) {
            return agentApplicationRepository.findByStatus(status);
        }
        return agentApplicationRepository.findAll();
    }

    private AgentApplication getApplication(Long applicationId) {
        return agentApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new UserNotFoundException("Application not found"));
    }

    private void ensureUserEligible(AppUser user) {
        if (user.getRole() == null || !"USER".equals(user.getRole().getName())) {
            throw new ForbiddenOperationException("Only users can apply to become agents");
        }
    }

    private void ensureNoActiveApplication(AppUser user, AgentApplicationType type) {
        agentApplicationRepository.findFirstByUserAndTypeOrderByCreatedAtDesc(user, type)
                .ifPresent(existing -> {
                    if (existing.getStatus() != AgentApplicationStatus.REJECTED) {
                        throw new ConflictException("You already have an active application");
                    }
                });
    }

    private List<Address> fetchAddresses(AppUser user, List<String> addressIds) {
        List<Address> addresses = addressRepository.findAllById(addressIds);
        if (addresses.size() != addressIds.size()) {
            throw new ForbiddenOperationException("One or more addresses not found");
        }
        Set<String> invalid = addresses.stream()
                .filter(address -> address.getUser() == null || !address.getUser().getId().equals(user.getId()))
                .map(Address::getId)
                .collect(Collectors.toSet());
        if (!invalid.isEmpty()) {
            throw new ForbiddenOperationException("Address does not belong to the current user");
        }
        if (addresses.stream().anyMatch(address -> Boolean.TRUE.equals(address.getDeleted()))) {
            throw new ForbiddenOperationException("One or more addresses are inactive");
        }
        return addresses;
    }

    private void validateAddressesHaveCoordinates(List<Address> addresses) {
        boolean missing = addresses.stream()
                .anyMatch(address -> address.getLatitude() == null || address.getLongitude() == null);
        if (missing) {
            throw new ForbiddenOperationException("All addresses must include latitude and longitude");
        }
    }
}
