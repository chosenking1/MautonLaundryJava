package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, String> {
}
