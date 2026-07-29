package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Module;
import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.repository.ModuleRepository;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.security.PermissionEvaluationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Creates and assigns modules (Permission Architecture V2, spec §8.2).
 *
 * <h2>The rule that matters</h2>
 * You cannot bundle a permission you do not hold, and you cannot assign a module
 * granting more than you hold (spec §3.2). Without that, a module is a
 * privilege-escalation ladder: anyone with MODULE_CREATE could bundle
 * PAYOUT_SETTLE and hand it to themselves.
 *
 * <h2>Why there is no fan-out</h2>
 * Spec §8.2 describes addPermissionToModule as "auto-grants to all current
 * module holders" and removePermissionFromModule as revoking from holders. No
 * such writes happen here, because PermissionEvaluationService resolves modules
 * by joining module_permissions at read time: adding a row already grants it to
 * every holder, and removing one already revokes it via the module path while an
 * individual GRANT still wins at evaluation step 2 -- which is exactly the
 * behaviour §3.2 asks for.
 *
 * <p>Materialising those grants instead would be strictly worse: two sources of
 * truth that drift the moment one write fails. What the read-time model does
 * need is cache invalidation, which is what every mutation here ends with.
 *
 * <h2>Not yet wired</h2>
 * Spec §2.2/§2.3 route an assignment beyond the actor's own permission set
 * through maker-checker rather than rejecting it. MakerCheckerService does not
 * exist yet, so this rejects instead -- the safe half of that rule. When
 * maker-checker lands, the throw sites marked below become the submit path.
 */
@Service
@RequiredArgsConstructor
public class ModuleService {

    private static final Logger log = LoggerFactory.getLogger(ModuleService.class);

    private final ModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionEvaluationService permissionEvaluationService;

    /**
     * @param permissionIds every one must be held by the creator (spec §3.2)
     * @throws IllegalArgumentException naming the offending permissions
     */
    @Transactional
    public Module createModule(String moduleKey, String displayName, String description,
                               Collection<Long> permissionIds, String actorId) {
        if (moduleRepository.findByModuleKey(moduleKey).isPresent()) {
            throw new IllegalArgumentException("A module with key '" + moduleKey + "' already exists.");
        }
        requireActorHolds(permissionIds, actorId);

        Module module = new Module();
        module.setId(UUID.randomUUID().toString());
        module.setModuleKey(moduleKey);
        module.setDisplayName(displayName);
        module.setDescription(description);
        module.setCreatedBy(actorId);
        module.setCreatedAt(LocalDateTime.now());
        moduleRepository.save(module);

        for (Long permissionId : dedupe(permissionIds)) {
            moduleRepository.addPermission(module.getId(), permissionId, actorId);
        }
        // No holders yet, so nothing to invalidate.
        log.info("Module {} created by {} with {} permission(s)", moduleKey, actorId, dedupe(permissionIds).size());
        return module;
    }

    /**
     * Adds a permission to a module. Every current holder gains it immediately,
     * except those with an exclusion for it -- see the class note on fan-out.
     */
    @Transactional
    public void addPermissionToModule(String moduleId, Long permissionId, String actorId) {
        Module module = require(moduleId);
        requireActorHolds(List.of(permissionId), actorId);

        moduleRepository.addPermission(moduleId, permissionId, actorId);
        invalidateHolders(moduleId,
                "permission " + permissionId + " added to module " + module.getModuleKey());
    }

    /**
     * Removes a permission from a module. Holders lose it via the module, but
     * keep it if they hold it individually (spec §3.2).
     */
    @Transactional
    public void removePermissionFromModule(String moduleId, Long permissionId, String actorId) {
        Module module = require(moduleId);
        // Removing is a de-escalation, so it needs no holds-check: taking away a
        // permission you do not have is not a privilege escalation.
        moduleRepository.removePermission(moduleId, permissionId);
        invalidateHolders(moduleId,
                "permission " + permissionId + " removed from module " + module.getModuleKey());
    }

    /**
     * @param exclusionPermissionIds permissions in the bundle the recipient must
     *                               NOT receive (spec §3.3). The actor may only
     *                               exclude; adding permissions outside the
     *                               module is a separate, explicit action.
     */
    @Transactional
    public void assignModuleToUser(String moduleId, String userId,
                                   Collection<Long> exclusionPermissionIds, String actorId) {
        Module module = require(moduleId);
        Set<Long> exclusions = dedupe(exclusionPermissionIds);
        requireActorHoldsEffectiveBundle(moduleId, exclusions, actorId);

        String assignmentId = UUID.randomUUID().toString();
        moduleRepository.assignToUser(assignmentId, userId, moduleId, actorId);
        // ON CONFLICT DO NOTHING means a re-assign keeps the original row, so
        // read the id back rather than assuming ours won.
        String existingId = moduleRepository.findUserAssignmentId(userId, moduleId).orElse(assignmentId);
        for (Long permissionId : exclusions) {
            moduleRepository.excludeForUser(UUID.randomUUID().toString(), existingId, permissionId, actorId);
        }

        permissionEvaluationService.invalidate(userId);
        log.info("Module {} assigned to user {} by {} with {} exclusion(s)",
                module.getModuleKey(), userId, actorId, exclusions.size());
    }

    @Transactional
    public void assignModuleToRole(String moduleId, Long roleId,
                                   Collection<Long> exclusionPermissionIds, String actorId) {
        Module module = require(moduleId);
        Set<Long> exclusions = dedupe(exclusionPermissionIds);
        requireActorHoldsEffectiveBundle(moduleId, exclusions, actorId);

        String assignmentId = UUID.randomUUID().toString();
        moduleRepository.assignToRole(assignmentId, roleId, moduleId, actorId);
        String existingId = moduleRepository.findRoleAssignmentId(roleId, moduleId).orElse(assignmentId);
        for (Long permissionId : exclusions) {
            moduleRepository.excludeForRole(UUID.randomUUID().toString(), existingId, permissionId, actorId);
        }

        // Every member of the role is affected, not just one user.
        invalidateHolders(moduleId, "module " + module.getModuleKey() + " assigned to role " + roleId);
    }

    // ---- guards ----

    /**
     * Spec §3.2: the actor must personally hold every permission involved.
     *
     * <p>Reports all offenders at once rather than the first: an admin fixing a
     * module one rejection at a time is how this gets worked around.
     */
    private void requireActorHolds(Collection<Long> permissionIds, String actorId) {
        Set<Long> ids = dedupe(permissionIds);
        if (ids.isEmpty()) {
            return;
        }
        Set<String> held = permissionEvaluationService.getEffectivePermissions(actorId);
        Set<String> missing = new TreeSet<>();
        for (Permission p : permissionRepository.findAllById(ids)) {
            if (!held.contains(p.getName())) {
                missing.add(p.getName());
            }
        }
        if (!missing.isEmpty()) {
            // Spec §2.3 would route this to maker-checker instead of rejecting.
            // Until MakerCheckerService exists, reject.
            throw new IllegalArgumentException(
                    "You cannot bundle or assign permissions you do not hold: "
                            + String.join(", ", missing));
        }
    }

    /** The actor must hold everything the recipient would actually receive. */
    private void requireActorHoldsEffectiveBundle(String moduleId, Set<Long> exclusions, String actorId) {
        List<Long> effective = moduleRepository.findPermissionIds(moduleId).stream()
                .filter(id -> !exclusions.contains(id))
                .collect(Collectors.toList());
        requireActorHolds(effective, actorId);
    }

    private Module require(String moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new IllegalArgumentException("Module not found: " + moduleId));
    }

    /**
     * Drops the cached permission set of everyone the module reaches.
     *
     * <p>The bundle change itself is already live -- resolution reads
     * module_permissions -- but a cached set would serve the old answer for up to
     * the TTL. For a removal that means a revoked permission still working.
     */
    private void invalidateHolders(String moduleId, String because) {
        List<String> holders = moduleRepository.findHolderUserIds(moduleId);
        holders.forEach(permissionEvaluationService::invalidate);
        log.info("Invalidated {} holder cache(s): {}", holders.size(), because);
    }

    private static Set<Long> dedupe(Collection<Long> ids) {
        return ids == null ? Set.of() : new LinkedHashSet<>(ids);
    }
}
