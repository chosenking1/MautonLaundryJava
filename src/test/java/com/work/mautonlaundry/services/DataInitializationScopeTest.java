package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.model.UserScope;
import com.work.mautonlaundry.data.model.enums.ScopeLevel;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.data.repository.RoleRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.data.repository.UserScopeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The bootstrap admin's data scope.
 *
 * <p>ScopeFilterService is closed-by-default, so an admin with no user_scope row
 * sees nothing at all -- silently, with no error. V17 seeds the admins that
 * existed when it ran; this covers the ones created afterwards.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataInitializationScopeTest {

    private static final String EMAIL = "admin@imototo.com";

    @Mock private PermissionRepository permissionRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserScopeRepository userScopeRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private DataInitializationService service;

    private Role adminRole;

    @BeforeEach
    void setUp() {
        adminRole = new Role("ADMIN");
        adminRole.setId(1L);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(permissionRepository.findAll()).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            if (u.getId() == null) u.setId("generated-id");
            return u;
        });
        config(EMAIL, "secret");
    }

    private void config(String email, String password) {
        ReflectionTestUtils.setField(service, "bootstrapAdminEmail", email);
        ReflectionTestUtils.setField(service, "bootstrapAdminPassword", password);
    }

    private AppUser existingAdmin(String id) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setEmail(EMAIL);
        u.setRole(adminRole);
        return u;
    }

    private void runInit() {
        ReflectionTestUtils.invokeMethod(service, "initializeAdminUser");
    }

    @Test
    void newBootstrapAdmin_getsNationalScope() {
        when(userRepository.findUserByEmail(EMAIL)).thenReturn(Optional.empty());
        when(userScopeRepository.existsById(anyString())).thenReturn(false);

        runInit();

        ArgumentCaptor<UserScope> captor = ArgumentCaptor.forClass(UserScope.class);
        verify(userScopeRepository).save(captor.capture());
        assertThat(captor.getValue().getScopeLevel()).isEqualTo(ScopeLevel.NATIONAL);
        assertThat(captor.getValue().getUserId()).isEqualTo("generated-id");
        // NATIONAL is the wildcard: no target may be set, or the V15 CHECK rejects it.
        assertThat(captor.getValue().getStateId()).isNull();
        assertThat(captor.getValue().getZoneId()).isNull();
        assertThat(captor.getValue().getRegionId()).isNull();
        assertThat(captor.getValue().getSpecialistUserId()).isNull();
    }

    @Test
    void existingAdminWithoutScope_getsOneToo() {
        // The regression this fixes: the old code returned early when the admin
        // already existed, so an admin created after V17 stayed scope-less and
        // saw nothing.
        when(userRepository.findUserByEmail(EMAIL)).thenReturn(Optional.of(existingAdmin("admin-1")));
        when(userScopeRepository.existsById("admin-1")).thenReturn(false);

        runInit();

        verify(userScopeRepository).save(any(UserScope.class));
        // Existing user must not be re-saved or re-passworded on every boot.
        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void existingScope_isNeverOverwritten() {
        // An admin deliberately narrowed to STATE must not be silently promoted
        // back to NATIONAL by a reboot.
        when(userRepository.findUserByEmail(EMAIL)).thenReturn(Optional.of(existingAdmin("admin-1")));
        when(userScopeRepository.existsById("admin-1")).thenReturn(true);

        runInit();

        verify(userScopeRepository, never()).save(any(UserScope.class));
    }

    @Test
    void noBootstrapConfig_doesNothing() {
        config("", "");

        runInit();

        verify(userRepository, never()).save(any(AppUser.class));
        verify(userScopeRepository, never()).save(any(UserScope.class));
    }

    @Test
    void blankPassword_doesNothing() {
        config(EMAIL, "   ");

        runInit();

        verify(userScopeRepository, never()).save(any(UserScope.class));
    }

    @Test
    void emailWithWhitespace_isTrimmedForBothLookupAndInsert() {
        // Previously existsByEmail() read the raw property while the insert wrote
        // a trimmed one, so a stray space meant the check never matched and every
        // boot retried the insert into a unique column.
        config("  " + EMAIL + "  ", "secret");
        when(userRepository.findUserByEmail(EMAIL)).thenReturn(Optional.of(existingAdmin("admin-1")));
        when(userScopeRepository.existsById("admin-1")).thenReturn(true);

        runInit();

        verify(userRepository).findUserByEmail(EMAIL);   // trimmed
        verify(userRepository, never()).save(any(AppUser.class));  // no duplicate insert
    }
}
