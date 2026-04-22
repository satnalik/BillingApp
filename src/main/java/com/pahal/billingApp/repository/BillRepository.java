package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillRepository extends JpaRepository<Bill,Long> {
}
