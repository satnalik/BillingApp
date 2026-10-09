package com.pahal.billingApp.repository;
import com.pahal.billingApp.entity.LicenseAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface LicenseAuditRepository extends JpaRepository<LicenseAudit, Long> {
    List<LicenseAudit> findTop100ByTenantIdOrderByIdDesc(String tenantId);
}
