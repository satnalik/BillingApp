package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.PurchaseBill;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseBillRepository extends JpaRepository<PurchaseBill, Long> {

    @EntityGraph(attributePaths = { "supplier", "items", "items.product", "payments" })
    List<PurchaseBill> findAllByOrderByBillDateDescIdDesc();

    @EntityGraph(attributePaths = { "supplier", "items", "items.product", "payments" })
    Optional<PurchaseBill> findWithDetailsById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "supplier", "items", "items.product", "payments" })
    @Query("select p from PurchaseBill p where p.id = :id")
    Optional<PurchaseBill> findWithDetailsByIdForUpdate(@Param("id") Long id);
}
