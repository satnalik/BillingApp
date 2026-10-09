package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {
    boolean existsByTenantIdAndProductId(String tenantId, Long productId);

    @Query("select distinct m.productId from StockMovement m where m.tenantId = :tenantId and m.productId in :ids")
    List<Long> findTrackedProductIds(@Param("tenantId") String tenantId, @Param("ids") Collection<Long> ids);
}
