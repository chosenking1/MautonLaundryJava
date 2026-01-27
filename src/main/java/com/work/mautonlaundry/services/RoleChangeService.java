package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.model.RoleChangeRequest;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.enums.RequestStatus;
import com.work.mautonlaundry.data.repository.RoleChangeRequestRepository;
import com.work.mautonlaundry.data.repository.RoleRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleChangeService {
    
    private final RoleChangeRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;

    @Transactional
    public RoleChangeRequest createRequest(String userId, String roleName) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        
        if (currentUser.getId().equals(userId)) {
            throw new RuntimeException("Admins cannot request role changes for themselves");
        }
        
        AppUser targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Role requestedRole = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found"));
        
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
    public RoleChangeRequest approveRequest(Long requestId) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        
        RoleChangeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        
        if (request.getRequestedByAdminId().equals(currentUser.getId())) {
            throw new RuntimeException("Maker cannot approve their own request");
        }
        
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Request is not pending");
        }
        
        request.setStatus(RequestStatus.APPROVED);
        request.setApprovedByAdminId(currentUser.getId());
        request.setUpdatedAt(LocalDateTime.now());
        
        // Apply role change
        AppUser user = request.getUser();
        user.setRole(request.getRequestedRole());
        userRepository.save(user);
        
        RoleChangeRequest saved = requestRepository.save(request);
        auditService.logAction("APPROVE_ROLE_REQUEST", "ROLE", saved.getId().toString());
        
        return saved;
    }

    @Transactional
    public RoleChangeRequest rejectRequest(Long requestId) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        
        RoleChangeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        
        if (request.getRequestedByAdminId().equals(currentUser.getId())) {
            throw new RuntimeException("Maker cannot reject their own request");
        }
        
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Request is not pending");
        }
        
        request.setStatus(RequestStatus.REJECTED);
        request.setApprovedByAdminId(currentUser.getId()); // Checker who rejected
        request.setUpdatedAt(LocalDateTime.now());
        
        RoleChangeRequest saved = requestRepository.save(request);
        auditService.logAction("REJECT_ROLE_REQUEST", "ROLE", saved.getId().toString());
        
        return saved;
    }

    public List<RoleChangeRequest> getPendingRequests() {
        return requestRepository.findByStatus(RequestStatus.PENDING);
    }
}
