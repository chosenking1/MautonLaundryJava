package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.model.UserScope;
import com.work.mautonlaundry.data.model.enums.ScopeLevel;
import com.work.mautonlaundry.data.repository.UserScopeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An ADMIN with no user_scope row holds every permission and can see nothing,
 * because ScopeFilterService is closed by default. That is what every admin
 * created after the initial seed silently became.
 */
@ExtendWith(MockitoExtension.class)
class ScopeProvisioningServiceTest {

    @Mock private UserScopeRepository userScopeRepository;
    @InjectMocks private ScopeProvisioningService service;

    private AppUser userWithRole(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        AppUser user = new AppUser();
        user.setId("user-1");
        user.setEmail("someone@imototo.com");
        user.setRole(role);
        return user;
    }

    @Test
    void newAdminGetsNationalScope() {
        when(userScopeRepository.existsById("user-1")).thenReturn(false);

        assertThat(service.ensureScopeForRole(userWithRole("ADMIN"))).isTrue();

        ArgumentCaptor<UserScope> saved = ArgumentCaptor.forClass(UserScope.class);
        verify(userScopeRepository).save(saved.capture());
        assertThat(saved.getValue().getScopeLevel()).isEqualTo(ScopeLevel.NATIONAL);
        assertThat(saved.getValue().getUserId()).isEqualTo("user-1");
        // assigned_by null is the established marker for system-seeded.
        assertThat(saved.getValue().getAssignedBy()).isNull();
    }

    @Test
    void anAdminNarrowedToOneStateIsNotWidenedBack() {
        // A later role change must not undo a deliberate restriction.
        when(userScopeRepository.existsById("user-1")).thenReturn(true);

        assertThat(service.ensureScopeForRole(userWithRole("ADMIN"))).isFalse();
        verify(userScopeRepository, never()).save(any());
    }

    @Test
    void nonAdminRolesGetNoGeographicScope() {
        // Agents are reached through assignment, not geography; handing them a
        // NATIONAL scope would widen what they can see.
        for (String role : new String[] {"USER", "LAUNDRY_AGENT", "DELIVERY_AGENT"}) {
            assertThat(service.ensureScopeForRole(userWithRole(role))).isFalse();
        }
        verify(userScopeRepository, never()).save(any());
    }

    @Test
    void incompleteUsersAreIgnoredRatherThanCrashing() {
        assertThat(service.ensureScopeForRole(null)).isFalse();
        assertThat(service.ensureScopeForRole(new AppUser())).isFalse();
        verify(userScopeRepository, never()).save(any());
    }
}
