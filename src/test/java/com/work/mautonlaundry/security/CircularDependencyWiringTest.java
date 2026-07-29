package com.work.mautonlaundry.security;

import com.work.mautonlaundry.data.repository.AuditLogRepository;
import com.work.mautonlaundry.data.repository.MakerCheckerRequestRepository;
import com.work.mautonlaundry.data.repository.PendingPermissionReviewRepository;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.data.repository.SystemConfigRepository;
import com.work.mautonlaundry.data.repository.TemporaryScopeGrantRepository;
import com.work.mautonlaundry.data.repository.UserPermissionRepository;
import com.work.mautonlaundry.data.repository.UserScopeRepository;
import com.work.mautonlaundry.data.repository.ZoneRepository;
import com.work.mautonlaundry.security.scope.ScopeFilterService;
import com.work.mautonlaundry.services.AuditService;
import com.work.mautonlaundry.services.UserAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * UserAccessService and MakerCheckerService depend on each other:
 * grant() defers to maker-checker when the actor lacks the permission, and an
 * approved request calls back into grantApproved(). The cycle is broken with
 * @Lazy -- but @Lazy sits on a Lombok @RequiredArgsConstructor field, and Lombok
 * only copies it to the generated constructor parameter (where Spring reads it)
 * when lombok.config lists it under copyableAnnotations.
 *
 * <p>Without that config the cycle is a hard one and the whole application fails
 * to start with BeanCurrentlyInCreationException -- which is exactly how it
 * failed on the first staging deploy, because no test had ever booted a Spring
 * context (the unit tests use mocks; @SpringBootTest was avoided because it runs
 * DataInitializationService against a database).
 *
 * <p>This is that missing check, in the cheapest faithful form: a real Spring
 * container wiring the five services involved through their real constructors,
 * with only leaf infrastructure (repositories, Redis) mocked -- so the actual
 * @Lazy cycle break is exercised, not stubbed around. No database.
 *
 * <p>It fails if the cycle is ever un-broken again: a lost lombok.config, a
 * removed @Lazy, or a new mutual dependency.
 */
class CircularDependencyWiringTest {

    // Only leaf infrastructure is mocked. The services are the real beans, wired
    // by Spring exactly as at boot -- which is the whole point.
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withBean(UserPermissionRepository.class, () -> mock(UserPermissionRepository.class))
            .withBean(UserScopeRepository.class, () -> mock(UserScopeRepository.class))
            .withBean(PermissionRepository.class, () -> mock(PermissionRepository.class))
            .withBean(PendingPermissionReviewRepository.class, () -> mock(PendingPermissionReviewRepository.class))
            .withBean(MakerCheckerRequestRepository.class, () -> mock(MakerCheckerRequestRepository.class))
            .withBean(SystemConfigRepository.class, () -> mock(SystemConfigRepository.class))
            .withBean(TemporaryScopeGrantRepository.class, () -> mock(TemporaryScopeGrantRepository.class))
            .withBean(ZoneRepository.class, () -> mock(ZoneRepository.class))
            .withBean(AuditLogRepository.class, () -> mock(AuditLogRepository.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            // The real services, constructed by Spring. The cycle lives between
            // the first two; the rest are their collaborators.
            .withBean(AuditService.class)
            .withBean(PermissionEvaluationService.class)
            .withBean(ScopeFilterService.class)
            .withBean(UserAccessService.class)
            .withBean(MakerCheckerService.class);

    @Test
    void theUserAccessAndMakerCheckerCycleResolves() {
        context.run(ctx -> assertThat(ctx)
                .as("context must start; a failure here means the @Lazy cycle break was lost "
                        + "(check lombok.config copyableAnnotations, or the @Lazy on "
                        + "UserAccessService/MakerCheckerService)")
                .hasNotFailed()
                .hasSingleBean(UserAccessService.class)
                .hasSingleBean(MakerCheckerService.class));
    }
}
