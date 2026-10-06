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
            where b.createdAt >= :start and b.createdAt < :end
              and (:tenantId is null or b.tenantId = :tenantId)
              and (b.status is null or b.status <> com.pahal.billingApp.enums.BillStatus.CANCELLED)
              and (:productName is null or lower(i.productName) like lower(concat('%', :productName, '%')))
              and (:barcode is null or lower(coalesce(i.barcode, p.barcode, '')) like lower(concat('%', :barcode, '%')))
              and (:category is null or lower(coalesce(p.category, '')) = lower(:category))
              and (:salesmanId is null or b.salesMan.employeeId = :salesmanId)
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
            where b.createdAt >= :start and b.createdAt < :end
              and (:tenantId is null or b.tenantId = :tenantId)
              and (b.status is null or b.status <> com.pahal.billingApp.enums.BillStatus.CANCELLED)
              and (:productName is null or lower(i.productName) like lower(concat('%', :productName, '%')))
              and (:barcode is null or lower(coalesce(i.barcode, p.barcode, '')) like lower(concat('%', :barcode, '%')))
              and (:category is null or lower(coalesce(p.category, '')) = lower(:category))
              and (:salesmanId is null or b.salesMan.employeeId = :salesmanId)
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
