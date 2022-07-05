package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Service;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<Service, Long> {

}
