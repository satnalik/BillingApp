package com.pahal.billingApp.repository;
import com.pahal.billingApp.entity.LicensedCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LicensedCounterRepository extends JpaRepository<LicensedCounter, String> {
    List<LicensedCounter> findByTenantIdOrderByName(String tenantId);
    Optional<LicensedCounter> findByIdAndTenantId(String id, String tenantId);
    long countByTenantIdAndActiveTrue(String tenantId);
}
