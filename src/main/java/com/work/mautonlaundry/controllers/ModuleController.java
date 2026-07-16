package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.Module;
import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.repository.ModuleRepository;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.security.PermissionEvaluationService;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.ModuleService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The API behind the Module Builder (Permission Architecture V2, spec §7.2).
 *
 * <p>Modules have been dead schema since V11: the tables existed and
 * PermissionEvaluationService resolved them, but nothing could write them. This
 * is what makes them usable.
 *
 * <p>The builder's left panel is {@link #myPermissions()} -- deliberately "the
 * permissions the caller holds", not all of them. Spec §3.2 forbids bundling a
 * permission you do not hold, and ModuleService enforces it, so offering the
 * full catalogue would only invite a rejection. §7.4 says the same of the user
 * management screen: "only shows permissions the current admin holds -- cannot
 * offer what you do not have".
 */
@RestController
@RequestMapping("/api/v1/admin/modules")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;
    private final ModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionEvaluationService permissionEvaluationService;

    @GetMapping
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('MODULE_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(moduleRepository.findAll().stream()
                .sorted(Comparator.comparing(Module::getModuleKey))
                .map(m -> Map.<String, Object>of(
                        "id", m.getId(),
                        "moduleKey", m.getModuleKey(),
                        "displayName", m.getDisplayName(),
                        "description", m.getDescription() == null ? "" : m.getDescription(),
                        "permissionCount", moduleRepository.findPermissionIds(m.getId()).size(),
                        "createdBy", m.getCreatedBy() == null ? "" : m.getCreatedBy(),
                        "createdAt", String.valueOf(m.getCreatedAt())))
                .toList());
    }

    @GetMapping("/{moduleId}")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('MODULE_VIEW')")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String moduleId) {
        Module m = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new IllegalArgumentException("Module not found: " + moduleId));
        List<Permission> permissions = permissionRepository.findAllById(moduleRepository.findPermissionIds(moduleId));
        return ResponseEntity.ok(Map.of(
                "id", m.getId(),
                "moduleKey", m.getModuleKey(),
                "displayName", m.getDisplayName(),
                "description", m.getDescription() == null ? "" : m.getDescription(),
                "permissions", permissions.stream()
                        .sorted(Comparator.comparing(Permission::getName))
                        .map(ModuleController::permissionView).toList()));
    }

    /**
     * The builder's left panel: what this admin may put in a module.
     * Grouped by category so the panel can render sections (spec §7.2).
     */
    @GetMapping("/available-permissions")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('MODULE_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> myPermissions() {
        Set<String> held = permissionEvaluationService.getEffectivePermissions(SecurityUtil.getCurrentUserId());
        return ResponseEntity.ok(permissionRepository.findAll().stream()
                .filter(p -> held.contains(p.getName()))
                .sorted(Comparator.comparing((Permission p) -> p.getCategory() == null ? "" : p.getCategory())
                        .thenComparing(Permission::getName))
                .map(ModuleController::permissionView)
                .toList());
    }

    @PostMapping
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('MODULE_CREATE')")
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateModuleRequest request) {
        Module created = moduleService.createModule(
                request.moduleKey, request.displayName, request.description,
                request.permissionIds == null ? List.of() : request.permissionIds,
                SecurityUtil.getCurrentUserId());
        return ResponseEntity.ok(Map.of("id", created.getId(), "moduleKey", created.getModuleKey()));
    }

    /**
     * Adding reaches every current holder immediately -- modules resolve at read
     * time -- so this is a heavier action than it looks.
     */
    @PostMapping("/{moduleId}/permissions/{permissionId}")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('MODULE_EDIT')")
    public ResponseEntity<Void> addPermission(@PathVariable String moduleId, @PathVariable Long permissionId) {
        moduleService.addPermissionToModule(moduleId, permissionId, SecurityUtil.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{moduleId}/permissions/{permissionId}")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('MODULE_EDIT')")
    public ResponseEntity<Void> removePermission(@PathVariable String moduleId, @PathVariable Long permissionId) {
        moduleService.removePermissionFromModule(moduleId, permissionId, SecurityUtil.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{moduleId}/assign/user/{userId}")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('MODULE_ASSIGN')")
    public ResponseEntity<Void> assignToUser(@PathVariable String moduleId, @PathVariable String userId,
                                             @RequestBody(required = false) AssignRequest request) {
        moduleService.assignModuleToUser(moduleId, userId,
                request == null || request.exclusions == null ? List.of() : request.exclusions,
                SecurityUtil.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{moduleId}/assign/role/{roleId}")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('MODULE_ASSIGN')")
    public ResponseEntity<Void> assignToRole(@PathVariable String moduleId, @PathVariable Long roleId,
                                             @RequestBody(required = false) AssignRequest request) {
        moduleService.assignModuleToRole(moduleId, roleId,
                request == null || request.exclusions == null ? List.of() : request.exclusions,
                SecurityUtil.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    /** Surfaces ModuleService's escalation refusals as 400 with the offending names. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onInvalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    private static Map<String, Object> permissionView(Permission p) {
        return Map.of(
                "id", p.getId(),
                "name", p.getName(),
                "category", p.getCategory() == null ? "UNCATEGORISED" : p.getCategory(),
                "description", p.getDescription() == null ? "" : p.getDescription());
    }

    public static class CreateModuleRequest {
        @NotBlank public String moduleKey;
        @NotBlank public String displayName;
        public String description;
        /** Every one must be held by the caller (spec §3.2). */
        public List<Long> permissionIds;
    }

    public static class AssignRequest {
        /** Permissions in the bundle the recipient must NOT receive (spec §3.3). */
        public List<Long> exclusions;
    }
}
