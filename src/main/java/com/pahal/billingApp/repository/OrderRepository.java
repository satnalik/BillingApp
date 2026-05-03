package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
        SELECT DATE(o.createdAt),
               SUM(o.totalAmount),
               COUNT(o.id)
        FROM Order o
        WHERE o.createdAt BETWEEN :startDate AND :endDate
        GROUP BY DATE(o.createdAt)
        ORDER BY DATE(o.createdAt)
    """)
    List<Object[]> findSalesSummary(LocalDate startDate, LocalDate endDate);
}
