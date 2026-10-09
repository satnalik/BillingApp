package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.ShiftCashMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ShiftCashMovementRepository extends JpaRepository<ShiftCashMovement, Long> {
    List<ShiftCashMovement> findByTenantIdAndShiftIdOrderByIdAsc(String tenantId, Long shiftId);
    Optional<ShiftCashMovement> findByTenantIdAndShiftIdAndRequestKey(String tenantId, Long shiftId, String key);
}
