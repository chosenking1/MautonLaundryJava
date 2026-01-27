package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {
    Optional<Permission> findByName(String name);
    Optional<Permission> findByResourceAndAction(String resource, String action);
}