package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.CashierShift;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CashierShiftRepository extends JpaRepository<CashierShift, Long>, JpaSpecificationExecutor<CashierShift> {
    boolean existsByTenantIdAndLicenseCounterIdAndClosedAtIsNull(String tenantId, String counterId);
    Optional<CashierShift> findFirstByTenantIdAndCashierUserIdAndClosedAtIsNullOrderByIdDesc(String tenantId, String userId);
    Optional<CashierShift> findByTenantIdAndCashierUserIdAndOpenRequestKey(String tenantId, String userId, String key);
    Optional<CashierShift> findByIdAndTenantId(Long id, String tenantId);
    @Query("select s.cashierUserId from CashierShift s where s.id = :id and s.tenantId = :tenant")
    Optional<String> findOwner(@Param("id") Long id, @Param("tenant") String tenant);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CashierShift s where s.id = :id and s.tenantId = :tenant")
    Optional<CashierShift> findLocked(@Param("id") Long id, @Param("tenant") String tenant);
}
