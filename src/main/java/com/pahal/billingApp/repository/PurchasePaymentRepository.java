package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.PurchasePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchasePaymentRepository extends JpaRepository<PurchasePayment, Long> {
}
