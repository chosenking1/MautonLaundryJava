package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Module;
import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.repository.ModuleRepository;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.security.PermissionEvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The escalation guard and the cache invalidation -- the two things that make
 * modules safe.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModuleServiceTest {

    private static final String ACTOR = "actor-1";
    private static final String MODULE = "mod-1";

    @Mock private ModuleRepository moduleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PermissionEvaluationService permissionEvaluationService;

    @InjectMocks private ModuleService service;

    private Module module;

    @BeforeEach
    void setUp() {
        module = new Module();
        module.setId(MODULE);
        module.setModuleKey("HQ_FINANCE");
        when(moduleRepository.findById(MODULE)).thenReturn(Optional.of(module));
        when(moduleRepository.findByModuleKey(anyString())).thenReturn(Optional.empty());
        when(moduleRepository.findHolderUserIds(MODULE)).thenReturn(List.of("holder-1", "holder-2"));
        actorHolds("PAYOUT_VIEW", "ORDER_VIEW");
    }

    private void actorHolds(String... names) {
        when(permissionEvaluationService.getEffectivePermissions(ACTOR)).thenReturn(Set.of(names));
    }

    private Permission perm(long id, String name) {
        Permission p = new Permission();
        p.setId(id);
        p.setName(name);
        return p;
    }

    private void permissionsAre(Permission... ps) {
        when(permissionRepository.findAllById(any())).thenReturn(List.of(ps));
    }

    // ---- the escalation guard (spec §3.2) ----

    @Test
    void cannotBundleAPermissionTheCreatorDoesNotHold() {
        // Without this, MODULE_CREATE is a ladder: bundle PAYOUT_SETTLE, assign
        // it to yourself.
        permissionsAre(perm(1, "PAYOUT_VIEW"), perm(2, "PAYOUT_SETTLE"));

        assertThatThrownBy(() -> service.createModule("HQ_FIN", "HQ Finance", null, List.of(1L, 2L), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PAYOUT_SETTLE");

        verify(moduleRepository, never()).save(any());
    }

    @Test
    void escalationMessageNamesEveryOffenderNotJustTheFirst() {
        // An admin fixing a module one rejection at a time is how this gets
        // worked around.
        permissionsAre(perm(1, "PAYOUT_SETTLE"), perm(2, "PAYOUT_REVERSE"));

        assertThatThrownBy(() -> service.createModule("X", "X", null, List.of(1L, 2L), ACTOR))
                .hasMessageContaining("PAYOUT_REVERSE")
                .hasMessageContaining("PAYOUT_SETTLE");
    }

    @Test
    void createsWhenTheCreatorHoldsEverything() {
        permissionsAre(perm(1, "PAYOUT_VIEW"), perm(2, "ORDER_VIEW"));

        Module created = service.createModule("HQ_FIN", "HQ Finance", "desc", List.of(1L, 2L), ACTOR);

        assertThat(created.getModuleKey()).isEqualTo("HQ_FIN");
        assertThat(created.getCreatedBy()).isEqualTo(ACTOR);
        assertThat(created.getId()).isNotBlank();
        verify(moduleRepository).addPermission(eq(created.getId()), eq(1L), eq(ACTOR));
        verify(moduleRepository).addPermission(eq(created.getId()), eq(2L), eq(ACTOR));
    }

    @Test
    void duplicateModuleKeyIsRejected() {
        when(moduleRepository.findByModuleKey("HQ_FIN")).thenReturn(Optional.of(module));

        assertThatThrownBy(() -> service.createModule("HQ_FIN", "x", null, List.of(), ACTOR))
                .hasMessageContaining("already exists");
    }

    @Test
    void cannotAddAPermissionTheActorDoesNotHold() {
        permissionsAre(perm(9, "PAYOUT_SETTLE"));

        assertThatThrownBy(() -> service.addPermissionToModule(MODULE, 9L, ACTOR))
                .hasMessageContaining("PAYOUT_SETTLE");

        verify(moduleRepository, never()).addPermission(anyString(), anyLong(), anyString());
    }

    // ---- assignment with exclusions (spec §3.3) ----

    @Test
    void exclusionsLetYouAssignAModuleYouDoNotFullyHold() {
        // The bundle contains PAYOUT_SETTLE, which the actor lacks -- excluding
        // it means the recipient never receives it, so there is nothing to
        // escalate and the assignment is legitimate.
        when(moduleRepository.findPermissionIds(MODULE)).thenReturn(List.of(1L, 9L));
        permissionsAre(perm(1, "PAYOUT_VIEW"));
        when(moduleRepository.findUserAssignmentId("user-9", MODULE)).thenReturn(Optional.of("asg-1"));

        service.assignModuleToUser(MODULE, "user-9", List.of(9L), ACTOR);

        verify(moduleRepository).excludeForUser(anyString(), eq("asg-1"), eq(9L), eq(ACTOR));
        verify(permissionEvaluationService).invalidate("user-9");
    }

    @Test
    void assigningAModuleWithAnUnheldPermissionAndNoExclusionIsRejected() {
        when(moduleRepository.findPermissionIds(MODULE)).thenReturn(List.of(1L, 9L));
        permissionsAre(perm(1, "PAYOUT_VIEW"), perm(9, "PAYOUT_SETTLE"));

        assertThatThrownBy(() -> service.assignModuleToUser(MODULE, "user-9", List.of(), ACTOR))
                .hasMessageContaining("PAYOUT_SETTLE");
    }

    @Test
    void reassigningKeepsTheOriginalAssignmentRow() {
        // ON CONFLICT DO NOTHING means our generated id may lose; exclusions must
        // hang off whichever row actually exists.
        when(moduleRepository.findPermissionIds(MODULE)).thenReturn(List.of(1L));
        permissionsAre(perm(1, "PAYOUT_VIEW"));
        when(moduleRepository.findUserAssignmentId("user-9", MODULE)).thenReturn(Optional.of("pre-existing"));

        service.assignModuleToUser(MODULE, "user-9", List.of(1L), ACTOR);

        verify(moduleRepository).excludeForUser(anyString(), eq("pre-existing"), eq(1L), eq(ACTOR));
    }

    // ---- cache invalidation (the only fan-out that is needed) ----

    @Test
    void addingAPermissionInvalidatesEveryHolder() {
        // The grant itself is already live (modules resolve at read time); the
        // stale cache is the only thing standing in the way.
        permissionsAre(perm(1, "PAYOUT_VIEW"));

        service.addPermissionToModule(MODULE, 1L, ACTOR);

        verify(permissionEvaluationService).invalidate("holder-1");
        verify(permissionEvaluationService).invalidate("holder-2");
    }

    @Test
    void removingAPermissionInvalidatesEveryHolder() {
        // Matters more than adding: a stale cache here keeps a revoked
        // permission working until the TTL expires.
        service.removePermissionFromModule(MODULE, 1L, ACTOR);

        verify(moduleRepository).removePermission(MODULE, 1L);
        verify(permissionEvaluationService).invalidate("holder-1");
        verify(permissionEvaluationService).invalidate("holder-2");
    }

    @Test
    void removingAPermissionNeedsNoHoldsCheck() {
        // De-escalation: taking away a permission you do not have is not an
        // escalation, so it must not be blocked by the guard.
        actorHolds();  // actor holds nothing at all

        service.removePermissionFromModule(MODULE, 99L, ACTOR);

        verify(moduleRepository).removePermission(MODULE, 99L);
    }

    @Test
    void assigningToARoleInvalidatesEveryMemberNotJustOne() {
        when(moduleRepository.findPermissionIds(MODULE)).thenReturn(List.of(1L));
        permissionsAre(perm(1, "PAYOUT_VIEW"));
        when(moduleRepository.findRoleAssignmentId(5L, MODULE)).thenReturn(Optional.of("rasg-1"));

        service.assignModuleToRole(MODULE, 5L, List.of(), ACTOR);

        verify(permissionEvaluationService).invalidate("holder-1");
        verify(permissionEvaluationService).invalidate("holder-2");
    }

    @Test
    void unknownModuleIsRejected() {
        when(moduleRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addPermissionToModule("nope", 1L, ACTOR))
                .hasMessageContaining("Module not found");
    }
}
