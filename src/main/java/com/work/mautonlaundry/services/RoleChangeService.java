package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AgentApplication;
import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.model.RoleChangeRequest;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.enums.AgentApplicationStatus;
import com.work.mautonlaundry.data.model.enums.AgentApplicationType;
import com.work.mautonlaundry.data.model.enums.InspectionStatus;
import com.work.mautonlaundry.data.model.enums.RequestStatus;
import com.work.mautonlaundry.data.repository.AgentApplicationRepository;
import com.work.mautonlaundry.data.repository.RoleChangeRequestRepository;
import com.work.mautonlaundry.data.repository.RoleRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.exceptions.ConflictException;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoleChangeService {
    
    private final RoleChangeRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;
    private final AgentApplicationRepository agentApplicationRepository;
    private final NotificationService notificationService;

    @Value("${app.admin.team-email:}")
    private String adminTeamEmail;

    @Transactional
    public RoleChangeRequest createRequest(String userId, String roleName) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        
        if (currentUser.getId().equals(userId)) {
            throw new ForbiddenOperationException("Admins cannot request role changes for themselves");
        }
        
        AppUser targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        
        Role requestedRole = roleRepository.findByName(roleName)
                .orElseThrow(() -> new NoSuchElementException("Role not found"));
        
        RoleChangeRequest request = new RoleChangeRequest();
        request.setUser(targetUser);
        request.setRequestedRole(requestedRole);
        request.setRequestedByAdminId(currentUser.getId());
        request.setStatus(RequestStatus.PENDING);
        
        RoleChangeRequest saved = requestRepository.save(request);
        auditService.logAction("CREATE_ROLE_REQUEST", "ROLE", saved.getId().toString());
        
        return saved;
    }

    @Transactional
    public RoleChangeRequest createRequestForApplication(Long applicationId) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();

        AgentApplication application = agentApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("Application not found"));

        if (application.getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenOperationException("Admins cannot request role changes for themselves");
        }

        if (application.getStatus() == AgentApplicationStatus.ROLE_CHANGE_REQUESTED
                || application.getStatus() == AgentApplicationStatus.APPROVED) {
            throw new ConflictException("Role change already requested or approved for this application");
        }
        if (application.getStatus() == AgentApplicationStatus.REJECTED) {
            throw new ConflictException("Cannot create role request for rejected application");
        }

        if (application.getType() == AgentApplicationType.LAUNDRY_AGENT
                && application.getInspectionStatus() != InspectionStatus.PASSED) {
            throw new ForbiddenOperationException("Laundry application must pass inspection before role change request");
        }

        if (application.getType() == AgentApplicationType.DELIVERY_AGENT
                && application.getInspectionStatus() == InspectionStatus.FAILED) {
            throw new ForbiddenOperationException("Delivery application failed inspection");
        }

        AppUser targetUser = application.getUser();
        Role requestedRole = roleRepository.findByName(application.getType().name())
                .orElseThrow(() -> new NoSuchElementException("Role not found"));

        RoleChangeRequest request = new RoleChangeRequest();
        request.setUser(targetUser);
        request.setRequestedRole(requestedRole);
        request.setRequestedByAdminId(currentUser.getId());
        request.setStatus(RequestStatus.PENDING);
        request.setAgentApplication(application);

        RoleChangeRequest saved = requestRepository.save(request);
        application.setStatus(AgentApplicationStatus.ROLE_CHANGE_REQUESTED);
        agentApplicationRepository.save(application);

        auditService.logAction("CREATE_ROLE_REQUEST", "ROLE", saved.getId().toString());
        return saved;
    }

    @Transactional
    public RoleChangeRequest approveRequest(Long requestId) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        
        RoleChangeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Request not found"));
        
        if (request.getRequestedByAdminId().equals(currentUser.getId())) {
            throw new ForbiddenOperationException("Maker cannot approve their own request");
        }
        
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ConflictException("Request is not pending");
        }
        
        request.setStatus(RequestStatus.APPROVED);
        request.setApprovedByAdminId(currentUser.getId());
        request.setUpdatedAt(LocalDateTime.now());
        
        // Apply role change
        AppUser user = request.getUser();
        user.setRole(request.getRequestedRole());
        userRepository.save(user);

        if (request.getAgentApplication() != null) {
            AgentApplication application = request.getAgentApplication();
            application.setStatus(AgentApplicationStatus.APPROVED);
            application.setRejectionReason(null);
            agentApplicationRepository.save(application);
            notificationService.notifyAgentApplicationApproved(user.getEmail(), application.getType().name());
        } else {
            String roleName = request.getRequestedRole().getName();
            if ("LAUNDRY_AGENT".equals(roleName) || "DELIVERY_AGENT".equals(roleName)) {
                notificationService.notifyAgentApplicationApproved(user.getEmail(), roleName);
            }
        }
        
        RoleChangeRequest saved = requestRepository.save(request);
        auditService.logAction("APPROVE_ROLE_REQUEST", "ROLE", saved.getId().toString());
        
        return saved;
    }

    @Transactional
    public RoleChangeRequest rejectRequest(Long requestId, String reason) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        
        RoleChangeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Request not found"));
        
        if (request.getRequestedByAdminId().equals(currentUser.getId())) {
            throw new ForbiddenOperationException("Maker cannot reject their own request");
        }
        
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ConflictException("Request is not pending");
        }
        
        request.setStatus(RequestStatus.REJECTED);
        request.setApprovedByAdminId(currentUser.getId()); // Checker who rejected
        request.setUpdatedAt(LocalDateTime.now());
        request.setRejectionReason(reason);

        if (request.getAgentApplication() != null) {
            AgentApplication application = request.getAgentApplication();
            application.setStatus(AgentApplicationStatus.REJECTED);
            application.setRejectionReason(reason);
            agentApplicationRepository.save(application);
            notificationService.notifyAgentApplicationRejected(
                    application.getUser().getEmail(),
                    application.getType().name(),
                    reason,
                    adminTeamEmail
            );
        } else {
            String roleName = request.getRequestedRole().getName();
            if ("LAUNDRY_AGENT".equals(roleName) || "DELIVERY_AGENT".equals(roleName)) {
                notificationService.notifyAgentApplicationRejected(
                        request.getUser().getEmail(),
                        roleName,
                        reason,
                        adminTeamEmail
                );
            }
        }
        
        RoleChangeRequest saved = requestRepository.save(request);
        auditService.logAction("REJECT_ROLE_REQUEST", "ROLE", saved.getId().toString());
        
        return saved;
    }

    public List<RoleChangeRequest> getPendingRequests() {
        List<RoleChangeRequest> all = requestRepository.findAll();
        List<RoleChangeRequest> pendingRaw = requestRepository.findDistinctByStatus(RequestStatus.PENDING);
        Map<Long, RoleChangeRequest> deduplicated = new LinkedHashMap<>();
        for (RoleChangeRequest request : pendingRaw) {
            deduplicated.put(request.getId(), request);
        }
        List<RoleChangeRequest> pending = List.copyOf(deduplicated.values());
        long approvedCount = all.stream().filter(req -> req.getStatus() == RequestStatus.APPROVED).count();
        long rejectedCount = all.stream().filter(req -> req.getStatus() == RequestStatus.REJECTED).count();

        log.info("Role requests summary -> total: {}, pendingRaw: {}, pendingDistinct: {}, approved: {}, rejected: {}",
                all.size(), pendingRaw.size(), pending.size(), approvedCount, rejectedCount);

        return pending;
    }
}
