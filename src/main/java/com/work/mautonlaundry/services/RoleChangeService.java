package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.model.RoleChangeRequest;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.enums.RequestStatus;
import com.work.mautonlaundry.data.repository.RoleChangeRequestRepository;
import com.work.mautonlaundry.data.repository.RoleRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.exceptions.ConflictException;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
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
        
        RoleChangeRequest saved = requestRepository.save(request);
        auditService.logAction("APPROVE_ROLE_REQUEST", "ROLE", saved.getId().toString());
        
        return saved;
    }

    @Transactional
    public RoleChangeRequest rejectRequest(Long requestId) {
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
