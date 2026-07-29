package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {

    /**
     * A numeric config value, or empty if absent or unparseable.
     *
     * <p>Swallows a bad value rather than throwing so callers fall back to their
     * default: a typo in one config row should not take down a scheduled job.
     */
    default Optional<Long> findLong(String key) {
        return findById(key).map(SystemConfig::getConfigValue).flatMap(v -> {
            try {
                return Optional.of(Long.parseLong(v.trim()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }
}
