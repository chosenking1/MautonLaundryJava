package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.BookingLaundryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingLaundryItemRepository extends JpaRepository<BookingLaundryItem, Long> {
    
    @Query("SELECT s.category.name, SUM(bli.totalPrice) FROM BookingLaundryItem bli " +
           "JOIN bli.service s " +
           "JOIN bli.booking b " +
           "WHERE b.deleted = false " +
           "GROUP BY s.category.name")
    List<Object[]> sumTotalPriceGroupByCategory();
    
    @Query("SELECT s.category.name, SUM(bli.totalPrice) FROM BookingLaundryItem bli " +
           "JOIN bli.service s " +
           "JOIN bli.booking b " +
           "WHERE b.deleted = false AND b.createdAt BETWEEN :start AND :end " +
           "GROUP BY s.category.name")
    List<Object[]> sumTotalPriceGroupByCategoryBetween(
            @Param("start") java.time.LocalDateTime start, 
            @Param("end") java.time.LocalDateTime end);
}
