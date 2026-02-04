package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    
    Optional<Permission> findByName(String name);
    
    Optional<Permission> findByEndpointAndMethodAndActiveTrue(String endpoint, String method);
    
    @Query("SELECT p FROM Permission p JOIN p.roles r WHERE p.endpoint = :endpoint AND p.method = :method AND r.name = :roleName AND p.active = true")
    Optional<Permission> findByEndpointAndMethodAndRoleName(@Param("endpoint") String endpoint, 
                                                           @Param("method") String method, 
                                                           @Param("roleName") String roleName);
}