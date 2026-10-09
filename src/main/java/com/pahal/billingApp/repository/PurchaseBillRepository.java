package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.PurchaseBill;
import com.pahal.billingApp.enums.PurchaseStatus;
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
    @Query("select p.id as id, p.billDate as billDate, p.billNumber as billNumber from PurchaseBill p where p.tenantId = :tenant and p.billDate >= :from and p.billDate <= :to and not exists (select d.id from GstDocument d where d.tenantId = :tenant and d.sourceKey = concat('purchase:', cast(p.id as String)))")
    List<com.pahal.billingApp.dto.GstDTO.LegacyPurchase> findLegacyGst(@Param("tenant") String tenant,
            @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

    @Query("select distinct i.product.id from PurchaseBill b join b.items i where b.tenantId = :tenantId and i.product.id in :ids")
    List<Long> findProductsWithStockActivity(@Param("tenantId") String tenantId, @Param("ids") java.util.Collection<Long> ids);

    @EntityGraph(attributePaths = { "supplier", "items", "items.product" })
    List<PurchaseBill> findAllByOrderByBillDateDescIdDesc();

    @EntityGraph(attributePaths = { "supplier", "items", "items.product" })
    Optional<PurchaseBill> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = { "supplier", "items", "items.product" })
    Optional<PurchaseBill> findWithDetailsByIdAndTenantId(Long id, String tenantId);

    @EntityGraph(attributePaths = { "supplier", "items", "items.product" })
    List<PurchaseBill> findByTenantIdOrderByBillDateDescIdDesc(String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "supplier", "items", "items.product" })
    @Query("select p from PurchaseBill p where p.id = :id")
    Optional<PurchaseBill> findWithDetailsByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseBill p where p.id = :id and p.tenantId = :tenantId")
    Optional<PurchaseBill> findScopedForUpdate(@Param("id") Long id, @Param("tenantId") String tenantId);

    @EntityGraph(attributePaths = {"supplier", "items", "items.product"})
    List<PurchaseBill> findBySupplierIdAndTenantIdOrderByBillDateAscIdAsc(Long supplierId, String tenantId);

    @Query("""
            select coalesce(sum(p.dueAmount), 0)
            from PurchaseBill p
            where (p.status is null or p.status <> :cancelled)
              and coalesce(p.dueAmount, 0) > 0
            """)
    Double sumActiveSupplierDueAmount(@Param("cancelled") PurchaseStatus cancelled);
}
