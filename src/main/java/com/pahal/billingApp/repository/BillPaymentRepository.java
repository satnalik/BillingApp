package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.BillPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BillPaymentRepository extends JpaRepository<BillPayment, Long> {

    @Query("""
            select p
            from BillPayment p
            where p.bill.id = :billId
            order by p.id asc
            """)
    List<BillPayment> findAllByBillIdOrderByIdAsc(@Param("billId") Long billId);

    @Query("""
            select p
            from BillPayment p
            where p.bill.id in :billIds
            order by p.bill.id asc, p.id asc
            """)
    List<BillPayment> findAllByBillIdInOrderByBillIdAscIdAsc(@Param("billIds") Collection<Long> billIds);
}

