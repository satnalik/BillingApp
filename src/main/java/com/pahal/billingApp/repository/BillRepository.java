package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.Bill;
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
            """)
    long countByCreatedAtBetween(@Param("start") LocalDateTime startInclusive,
                                 @Param("end") LocalDateTime endExclusive);

    @Query("""
            select coalesce(sum(b.totalAmount), 0)
            from Bill b
            where b.createdAt >= :start and b.createdAt < :end
            """)
    Double sumTotalAmountBetween(@Param("start") LocalDateTime startInclusive,
                                 @Param("end") LocalDateTime endExclusive);

    @Query("""
            select coalesce(sum(i.quantity), 0)
            from Bill b
            join b.items i
            where b.createdAt >= :start and b.createdAt < :end
            """)
    Long sumItemsQuantityBetween(@Param("start") LocalDateTime startInclusive,
                                 @Param("end") LocalDateTime endExclusive);

    @Query("""
            select i.productName as name, sum(i.quantity) as qty
            from Bill b
            join b.items i
            where b.createdAt >= :start and b.createdAt < :end
              and i.productName is not null
              and trim(i.productName) <> ''
            group by i.productName
            order by sum(i.quantity) desc
            """)
    List<ReportAggProjection> findProductBreakdown(@Param("start") LocalDateTime startInclusive,
                                                   @Param("end") LocalDateTime endExclusive);

    @Query("""
            select b.salesMan.employeeId as employeeId,
                   b.salesMan.name as name,
                   sum(coalesce(b.totalAmount, 0)) as revenue
            from Bill b
            where b.createdAt >= :start and b.createdAt < :end
              and b.salesMan is not null
              and b.salesMan.employeeId is not null
            group by b.salesMan.employeeId, b.salesMan.name
            order by sum(coalesce(b.totalAmount, 0)) desc
            """)
    List<ReportAggProjection> findSalesmanBreakdown(@Param("start") LocalDateTime startInclusive,
                                                    @Param("end") LocalDateTime endExclusive);

    @EntityGraph(attributePaths = {"items", "salesMan"})
    Optional<Bill> findWithDetailsById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"payments"})
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
