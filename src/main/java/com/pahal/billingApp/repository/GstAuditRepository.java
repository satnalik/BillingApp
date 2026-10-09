package com.pahal.billingApp.repository;
import com.pahal.billingApp.entity.GstAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface GstAuditRepository extends JpaRepository<GstAudit, Long> {
    List<GstAudit> findTop200ByTenantIdOrderByIdDesc(String tenant);
}
