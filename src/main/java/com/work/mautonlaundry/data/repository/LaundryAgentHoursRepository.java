package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.LaundryAgentHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface LaundryAgentHoursRepository extends JpaRepository<LaundryAgentHours, String> {
    List<LaundryAgentHours> findByUser_Id(String userId);
    Optional<LaundryAgentHours> findByUser_IdAndDayOfWeek(String userId, short dayOfWeek);

    @Transactional
    void deleteByUser_Id(String userId);
}
