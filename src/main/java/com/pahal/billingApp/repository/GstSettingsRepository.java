package com.pahal.billingApp.repository;
import com.pahal.billingApp.entity.GstSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface GstSettingsRepository extends JpaRepository<GstSettings, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GstSettings s where s.tenantId = :tenant")
    Optional<GstSettings> lock(@Param("tenant") String tenant);
}
