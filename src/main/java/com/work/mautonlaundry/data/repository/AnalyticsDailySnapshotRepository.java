package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.AnalyticsDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface AnalyticsDailySnapshotRepository extends JpaRepository<AnalyticsDailySnapshot, LocalDate> {

    List<AnalyticsDailySnapshot> findBySnapshotDateBetweenOrderBySnapshotDateAsc(LocalDate from, LocalDate to);

    /** Sum of daily revenue across a date range (inclusive). */
    @Query("SELECT COALESCE(SUM(s.totalRevenue), 0) FROM AnalyticsDailySnapshot s " +
            "WHERE s.snapshotDate BETWEEN :from AND :to")
    BigDecimal sumRevenueBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Sum of daily platform earnings across a date range (inclusive). */
    @Query("SELECT COALESCE(SUM(s.platformEarnings), 0) FROM AnalyticsDailySnapshot s " +
            "WHERE s.snapshotDate BETWEEN :from AND :to")
    BigDecimal sumPlatformEarningsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Sum of daily orders across a date range (inclusive). */
    @Query("SELECT COALESCE(SUM(s.totalOrders), 0) FROM AnalyticsDailySnapshot s " +
            "WHERE s.snapshotDate BETWEEN :from AND :to")
    Long sumOrdersBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Sum of daily new customers across a date range (inclusive). Summable to monthly new customers. */
    @Query("SELECT COALESCE(SUM(s.newCustomers), 0) FROM AnalyticsDailySnapshot s " +
            "WHERE s.snapshotDate BETWEEN :from AND :to")
    Long sumNewCustomersBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
