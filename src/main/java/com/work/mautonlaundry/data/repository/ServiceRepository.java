package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Services;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface ServiceRepository extends JpaRepository<Services, Long> {

        @Query("SELECT s FROM Services s WHERE s.service_name = :name")
        Optional<Services> findServicesByService_name(@Param("name") String name);



//    Optional<Services> findServicesByService_name(String service);
//    Optional<Services> deleteServicesByService_name(String service);

}
