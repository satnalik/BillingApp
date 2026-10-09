package com.pahal.billingApp.repository;
import com.pahal.billingApp.entity.TenantLicense;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface TenantLicenseRepository extends JpaRepository<TenantLicense, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from TenantLicense t where t.tenantId = :tenant")
    Optional<TenantLicense> lock(@Param("tenant") String tenant);
}
