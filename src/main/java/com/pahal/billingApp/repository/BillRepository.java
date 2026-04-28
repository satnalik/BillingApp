package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BillRepository extends JpaRepository<Bill,Long> {
    List<Bill> findByCreatedAtBetween(LocalDateTime startInclusive, LocalDateTime endExclusive);
}
