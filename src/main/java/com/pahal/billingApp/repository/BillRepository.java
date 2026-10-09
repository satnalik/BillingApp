package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.dto.ProductSalesAggProjection;
import com.pahal.billingApp.dto.ReportAggProjection;
import com.pahal.billingApp.dto.PaymentMethodAggProjection;
import com.pahal.billingApp.dto.SalesmanPaymentAggProjection;
import com.pahal.billingApp.enums.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill,Long> {
    Optional<Bill> findByTenantIdAndTaxDocumentNumber(String tenantId, String number);
    @Query("select b.id as id, b.createdAt as createdAt from Bill b where b.tenantId = :tenant and b.createdAt >= :from and b.createdAt < :to and b.taxDocumentNumber is null")
    List<com.pahal.billingApp.dto.GstDTO.LegacySale> findLegacyGst(@Param("tenant") String tenant,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    // Read every item of each invoice: allocate its final discount BEFORE product filters.
    @Query("""
            select b.id as billId, b.createdAt as createdAt, b.instantDiscountAmount as billDiscount,
                   i.finalDiscountAmount as finalDiscountAmount,
                   i.id as itemId, i.productId as productId, i.productName as productName,
                   i.barcode as barcode, p.category as category, i.quantity as quantity,
                   i.returnedQuantity as returnedQuantity, i.taxableAmount as taxableAmount,
                   i.gstAmount as gstAmount, i.cgstAmount as cgstAmount, i.sgstAmount as sgstAmount,
                   i.igstAmount as igstAmount, coalesce(i.unitSellingPrice, i.priceAtSale) as unitSellingPrice,
                   i.discount as discount, i.unitCostAtSale as unitCostAtSale
            from Bill b join b.items i
            left join Product p on p.id = i.productId and p.tenantId = b.tenantId
            where b.tenantId = :tenantId and b.createdAt >= :start and b.createdAt < :end
              and (b.status is null or b.status <> com.pahal.billingApp.enums.BillStatus.CANCELLED)
            order by b.id, i.id
            """)
    List<com.pahal.billingApp.dto.SalesProfitReportDTO.SaleRow> findProfitRows(
            @Param("tenantId") String tenantId, @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @EntityGraph(attributePaths = {"items", "salesMan"})
    @Query("select b from Bill b where b.tenantId = :tenantId and b.creationRequestKey = :key")
    Optional<Bill> findSubmission(@Param("tenantId") String tenantId, @Param("key") String key);

    @Query("""
            select b.id as id, b.customerName as customerName, b.contactInfo as contactInfo,
                   b.createdAt as createdAt, b.totalAmount as totalAmount,
                   b.paidAmount as paidAmount, b.dueAmount as dueAmount
            from Bill b where b.tenantId = :tenantId and b.dueAmount > 0
              and (b.status is null or b.status <> com.pahal.billingApp.enums.BillStatus.CANCELLED)
            order by b.createdAt desc, b.id desc
            """)
    List<com.pahal.billingApp.dto.OperationalReportDTO.OutstandingBillRow> findOutstandingReportRows(
            @Param("tenantId") String tenantId);

    @Query("select distinct i.productId from Bill b join b.items i where b.tenantId = :tenantId and i.productId in :ids")
    List<Long> findProductsWithStockActivity(@Param("tenantId") String tenantId, @Param("ids") java.util.Collection<Long> ids);

    List<Bill> findByCreatedAtBetween(LocalDateTime startInclusive, LocalDateTime endExclusive);

    @Query("""
            select count(b)
            from Bill b
            where b.createdAt >= :start and b.createdAt < :end
              and (b.status is null or b.status <> com.pahal.billingApp.enums.BillStatus.CANCELLED)
            """)
    long countByCreatedAtBetween(@Param("start") LocalDateTime startInclusive,
                                 @Param("end") LocalDateTime endExclusive);

    @Query("""
            select coalesce(sum(b.totalAmount), 0)
            from Bill b
            where b.createdAt >= :start and b.createdAt < :end
              and (b.status is null or b.status <> com.pahal.billingApp.enums.BillStatus.CANCELLED)
            """)
    Double sumTotalAmountBetween(@Param("start") LocalDateTime startInclusive,
                                 @Param("end") LocalDateTime endExclusive);

    @Query("""
            select coalesce(sum(coalesce(i.quantity, 0) - coalesce(i.returnedQuantity, 0)), 0)
            from Bill b
            join b.items i
            where b.createdAt >= :start and b.createdAt < :end
              and (b.status is null or b.status <> com.pahal.billingApp.enums.BillStatus.CANCELLED)
            """)
    Double sumItemsQuantityBetween(@Param("start") LocalDateTime startInclusive,
                                 @Param("end") LocalDateTime endExclusive);

    @Query("""
            select i.productName as name, sum(coalesce(i.quantity, 0) - coalesce(i.returnedQuantity, 0)) as qty
            from Bill b
            join b.items i
            where b.createdAt >= :start and b.createdAt < :end
              and (b.status is null or b.status <> com.pahal.billingApp.enums.BillStatus.CANCELLED)
              and i.productName is not null
              and trim(i.productName) <> ''
              and (coalesce(i.quantity, 0) - coalesce(i.returnedQuantity, 0)) > 0
            group by i.productName
            order by sum(coalesce(i.quantity, 0) - coalesce(i.returnedQuantity, 0)) desc
            """)
    List<ReportAggProjection> findProductBreakdown(@Param("start") LocalDateTime startInclusive,
                                                   @Param("end") LocalDateTime endExclusive);

    @Query("""
            select b.salesMan.employeeId as employeeId,
                   b.salesMan.name as name,
                   sum(coalesce(b.totalAmount, 0)) as revenue
            from Bill b
            where b.createdAt >= :start and b.createdAt < :end
              and (b.status is null or b.status <> com.pahal.billingApp.enums.BillStatus.CANCELLED)
              and b.salesMan is not null
              and b.salesMan.employeeId is not null
            group by b.salesMan.employeeId, b.salesMan.name
            order by sum(coalesce(b.totalAmount, 0)) desc
            """)
    List<ReportAggProjection> findSalesmanBreakdown(@Param("start") LocalDateTime startInclusive,
                                                    @Param("end") LocalDateTime endExclusive);

    // Explicit String casts keep optional null filters typed as text in PostgreSQL.
    @Query("""
            select i.productId as productId,
                   i.productName as productName,
                   coalesce(p.barcode, min(i.barcode)) as barcode,
                   p.category as category,
                   coalesce(p.hsnCode, max(i.hsnCode)) as hsnCode,
                   p.stockQuantity as currentStock,
                   coalesce(sum(coalesce(i.quantity, 0) - coalesce(i.returnedQuantity, 0)), 0) as quantitySold,
                   count(distinct b.id) as billsCount,
                   coalesce(sum(coalesce(i.unitSellingPrice, i.priceAtSale, 0) * (coalesce(i.quantity, 0) - coalesce(i.returnedQuantity, 0))), 0) as grossRevenue,
                   coalesce(sum((coalesce(i.unitSellingPrice, i.priceAtSale, 0) * (coalesce(i.quantity, 0) - coalesce(i.returnedQuantity, 0))) * coalesce(i.discount, 0) / 100), 0) as discountAmount,
                   coalesce(sum(coalesce(i.taxableAmount, (coalesce(i.unitSellingPrice, i.priceAtSale, 0) * coalesce(i.quantity, 0)) * (1 - coalesce(i.discount, 0) / 100)) * case when coalesce(i.quantity, 0) = 0 then 0 else ((coalesce(i.quantity, 0) - coalesce(i.returnedQuantity, 0)) / i.quantity) end), 0) as taxableRevenue,
                   coalesce(sum(coalesce(i.gstAmount, 0) * case when coalesce(i.quantity, 0) = 0 then 0 else ((coalesce(i.quantity, 0) - coalesce(i.returnedQuantity, 0)) / i.quantity) end), 0) as gstAmount,
                   coalesce(sum((coalesce(i.taxableAmount, (coalesce(i.unitSellingPrice, i.priceAtSale, 0) * coalesce(i.quantity, 0)) * (1 - coalesce(i.discount, 0) / 100)) + coalesce(i.gstAmount, 0)) * case when coalesce(i.quantity, 0) = 0 then 0 else ((coalesce(i.quantity, 0) - coalesce(i.returnedQuantity, 0)) / i.quantity) end), 0) as netRevenue
            from Bill b
            join b.items i
            left join Product p on p.id = i.productId and p.tenantId = b.tenantId
            left join b.salesMan sm
            where b.createdAt >= :start and b.createdAt < :end
              and b.tenantId = :tenantId
              and (b.status is null or b.status <> com.pahal.billingApp.enums.BillStatus.CANCELLED)
              and (cast(:productName as String) is null or lower(i.productName) like lower(concat('%', cast(:productName as String), '%')))
              and (cast(:barcode as String) is null or lower(coalesce(i.barcode, p.barcode, '')) like lower(concat('%', cast(:barcode as String), '%')))
              and (cast(:category as String) is null or lower(coalesce(p.category, '')) = lower(cast(:category as String)))
              and (cast(:salesmanId as String) is null or sm.employeeId = cast(:salesmanId as String))
              and i.productName is not null
              and trim(i.productName) <> ''
              and (coalesce(i.quantity, 0) - coalesce(i.returnedQuantity, 0)) > 0
            group by i.productId, i.productName, p.barcode, p.category, p.hsnCode, p.stockQuantity
            order by coalesce(sum((coalesce(i.taxableAmount, (coalesce(i.unitSellingPrice, i.priceAtSale, 0) * coalesce(i.quantity, 0)) * (1 - coalesce(i.discount, 0) / 100)) + coalesce(i.gstAmount, 0)) * case when coalesce(i.quantity, 0) = 0 then 0 else ((coalesce(i.quantity, 0) - coalesce(i.returnedQuantity, 0)) / i.quantity) end), 0) desc
            """)
    List<ProductSalesAggProjection> findProductSales(
            @Param("tenantId") String tenantId,
            @Param("start") LocalDateTime startInclusive,
            @Param("end") LocalDateTime endExclusive,
            @Param("productName") String productName,
            @Param("barcode") String barcode,
            @Param("category") String category,
            @Param("salesmanId") String salesmanId);

    @Query("""
            select count(distinct b.id)
            from Bill b
            join b.items i
            left join Product p on p.id = i.productId and p.tenantId = b.tenantId
            left join b.salesMan sm
            where b.createdAt >= :start and b.createdAt < :end
              and b.tenantId = :tenantId
              and (b.status is null or b.status <> com.pahal.billingApp.enums.BillStatus.CANCELLED)
              and (cast(:productName as String) is null or lower(i.productName) like lower(concat('%', cast(:productName as String), '%')))
              and (cast(:barcode as String) is null or lower(coalesce(i.barcode, p.barcode, '')) like lower(concat('%', cast(:barcode as String), '%')))
              and (cast(:category as String) is null or lower(coalesce(p.category, '')) = lower(cast(:category as String)))
              and (cast(:salesmanId as String) is null or sm.employeeId = cast(:salesmanId as String))
              and i.productName is not null
              and trim(i.productName) <> ''
              and (coalesce(i.quantity, 0) - coalesce(i.returnedQuantity, 0)) > 0
            """)
    long countProductSalesBills(
            @Param("tenantId") String tenantId,
            @Param("start") LocalDateTime startInclusive,
            @Param("end") LocalDateTime endExclusive,
            @Param("productName") String productName,
            @Param("barcode") String barcode,
            @Param("category") String category,
            @Param("salesmanId") String salesmanId);

    @EntityGraph(attributePaths = {"items", "salesMan"})
    Optional<Bill> findWithDetailsById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"items", "payments", "salesMan"})
    @Query("select b from Bill b where b.id = :id")
    Optional<Bill> findWithDetailsByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"items", "salesMan"})
    List<Bill> findAllByOrderByCreatedAtDesc();

    @Query("""
            select p.method as method, coalesce(sum(p.amount), 0) as amount
            from BillPayment p
            join p.bill b
            where b.createdAt >= :start and b.createdAt < :end
              and b.tenantId = :tenantId
              and (b.status is null or b.status <> com.pahal.billingApp.enums.BillStatus.CANCELLED)
              and p.method <> :exclude
            group by p.method
            """)
    List<PaymentMethodAggProjection> sumPaymentsByMethodExcluding(@Param("tenantId") String tenantId,
                                                                  @Param("start") LocalDateTime startInclusive,
                                                                  @Param("end") LocalDateTime endExclusive,
                                                                  @Param("exclude") PaymentMethod exclude);

    @Query("""
            select coalesce(sum(p.amount), 0)
            from BillPayment p
            join p.bill b
            where b.createdAt >= :start and b.createdAt < :end
              and b.tenantId = :tenantId
              and (b.status is null or b.status <> com.pahal.billingApp.enums.BillStatus.CANCELLED)
              and p.method = :method
            """)
    Double sumPaymentsByMethod(@Param("tenantId") String tenantId,
                               @Param("start") LocalDateTime startInclusive,
                               @Param("end") LocalDateTime endExclusive,
                               @Param("method") PaymentMethod method);

    @Query("""
            select coalesce(sum(b.dueAmount), 0)
            from Bill b
            where coalesce(b.dueAmount, 0) > 0
              and (b.status is null or b.status <> com.pahal.billingApp.enums.BillStatus.CANCELLED)
            """)
    Double sumOutstandingDueAmount();

    @Query("""
            select b.salesMan.employeeId as employeeId,
                   b.salesMan.name as name,
                   p.method as method,
                   coalesce(sum(p.amount), 0) as amount
            from BillPayment p
            join p.bill b
            where b.createdAt >= :start and b.createdAt < :end
              and b.tenantId = :tenantId
              and (b.status is null or b.status <> com.pahal.billingApp.enums.BillStatus.CANCELLED)
              and b.salesMan is not null
              and b.salesMan.employeeId is not null
              and p.method <> :exclude
            group by b.salesMan.employeeId, b.salesMan.name, p.method
            """)
    List<SalesmanPaymentAggProjection> sumSalesmanPaymentsByMethodExcluding(@Param("tenantId") String tenantId,
                                                                            @Param("start") LocalDateTime startInclusive,
                                                                            @Param("end") LocalDateTime endExclusive,
                                                                            @Param("exclude") PaymentMethod exclude);

    @Query("""
            select b.salesMan.employeeId as employeeId,
                   b.salesMan.name as name,
                   p.method as method,
                   coalesce(sum(p.amount), 0) as amount
            from BillPayment p
            join p.bill b
            where b.createdAt >= :start and b.createdAt < :end
              and b.tenantId = :tenantId
              and (b.status is null or b.status <> com.pahal.billingApp.enums.BillStatus.CANCELLED)
              and b.salesMan is not null
              and b.salesMan.employeeId is not null
              and p.method = :method
            group by b.salesMan.employeeId, b.salesMan.name, p.method
            """)
    List<SalesmanPaymentAggProjection> sumSalesmanPaymentsByMethod(@Param("tenantId") String tenantId,
                                                                   @Param("start") LocalDateTime startInclusive,
                                                                   @Param("end") LocalDateTime endExclusive,
                                                                   @Param("method") PaymentMethod method);
}
