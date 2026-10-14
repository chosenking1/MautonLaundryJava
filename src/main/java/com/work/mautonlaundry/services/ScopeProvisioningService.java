package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.UserScope;
import com.work.mautonlaundry.data.model.enums.ScopeLevel;
import com.work.mautonlaundry.data.repository.UserScopeRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Gives a newly promoted administrator a data scope.
 *
 * <p>Permissions and scope are separate dimensions, and a role carries only the
 * first. ScopeFilterService is closed by default -- "no scope assigned means no
 * data, never all data" -- so an ADMIN with no user_scope row holds every
 * permission in the system and can see none of it: bookings, customers and
 * orders all come back empty with nothing to indicate why. That is not a
 * security boundary, it is a broken account, and it is what every admin created
 * after the initial seed silently became.
 *
 * <p>NATIONAL is the right default rather than the narrowest one: ADMIN is
 * already granted every permission at startup, so withholding geography protects
 * nothing while making the account useless. Narrower scopes remain assignable
 * afterwards -- this only fills the gap where there was no row at all.
 *
 * <p>An existing row is never overwritten: an admin deliberately narrowed to one
 * state must not be widened back to NATIONAL by a later role change. Same rule
 * as the bootstrap admin's seeding, and as V17's ON CONFLICT DO NOTHING.
 */
@Service
@RequiredArgsConstructor
public class ScopeProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(ScopeProvisioningService.class);

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserScopeRepository userScopeRepository;

    /**
     * Ensures {@code user} can see the data their role implies. A no-op for any
     * role other than ADMIN, and for an admin who already has a scope.
     *
     * @return true when a scope was created
     */
    @Transactional
    public boolean ensureScopeForRole(AppUser user) {
        if (user == null || user.getId() == null || user.getRole() == null) {
            return false;
        }
        if (!ADMIN_ROLE.equalsIgnoreCase(user.getRole().getName())) {
            return false;
        }
        if (userScopeRepository.existsById(user.getId())) {
            return false;
        }

        UserScope scope = new UserScope();
        scope.setUserId(user.getId());
        scope.setScopeLevel(ScopeLevel.NATIONAL);
        // assigned_by stays null, the established marker for system-seeded.
        scope.setAssignedAt(LocalDateTime.now());
        userScopeRepository.save(scope);

        log.info("Assigned NATIONAL scope to new administrator {}", user.getEmail());
        return true;
    }
}
