package com.pahal.billingApp.service;

import com.pahal.billingApp.dto.SupplierStatementDTO;
import com.pahal.billingApp.entity.PurchaseBill;
import com.pahal.billingApp.enums.PurchaseStatus;
import com.pahal.billingApp.repository.PurchaseBillRepository;
import com.pahal.billingApp.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import static com.pahal.billingApp.service.PurchaseAmounts.*;

@Service
public class SupplierStatementService {
    @org.springframework.beans.factory.annotation.Autowired private com.pahal.billingApp.licensing.ModuleAccessService moduleAccess;
    private final SupplierRepository suppliers;
    private final PurchaseBillRepository purchases;
    public SupplierStatementService(SupplierRepository suppliers, PurchaseBillRepository purchases) {
        this.suppliers = suppliers;
        this.purchases = purchases;
    }
    private record Event(LocalDateTime date, int priority, long id, String type, PurchaseBill bill,
                         String reference, String description, BigDecimal delta) {}

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public SupplierStatementDTO statement(Long supplierId, LocalDate from, LocalDate to) {
        moduleAccess.requireHistory(com.pahal.billingApp.licensing.Feature.SUPPLIER_STATEMENTS);
        String tenant = StockService.requireTenant();
        LocalDate end = to == null ? LocalDate.now() : to;
        if (from != null && from.isAfter(end)) throw new IllegalArgumentException("Start date must be on or before end date.");
        var supplier = suppliers.findByIdAndTenantId(supplierId, tenant)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found."));
        var bills = purchases.findBySupplierIdAndTenantIdOrderByBillDateAscIdAsc(supplierId, tenant);
        List<Event> events = new ArrayList<>();
        double currentDue = 0, currentCredit = 0;
        for (var bill : bills) {
            events.add(new Event(bill.getBillDate().atStartOfDay(), 0, bill.getId(), "PURCHASE", bill,
                    bill.getBillNumber(), "Supplier purchase", money(value(bill.getTotalAmount()))));
            for (var payment : bill.getPayments()) {
                boolean refund = Boolean.TRUE.equals(payment.getRefund());
                LocalDateTime date = payment.getCreatedAt() == null ? bill.getCreatedAt() : payment.getCreatedAt();
                if (date == null) date = bill.getBillDate().atStartOfDay();
                events.add(new Event(date, refund ? 3 : 1, payment.getId(), refund ? "REFUND" : "PAYMENT", bill,
                        payment.getReference(), (refund ? "Supplier refund received · " : "Payment to supplier · ") + payment.getMethod(),
                        money(value(payment.getAmount())).multiply(BigDecimal.valueOf(refund ? 1 : -1))));
            }
            for (var returned : bill.getReturns()) {
                events.add(new Event(returned.getCreatedAt(), 2, returned.getId(), "RETURN", bill,
                        "PR-" + returned.getId(), returned.getReason(), money(value(returned.getCreditAmount())).negate()));
            }
            if (bill.getStatus() == PurchaseStatus.CANCELLED) {
                LocalDateTime date = bill.getCancelledAt() != null ? bill.getCancelledAt() : bill.getCreatedAt();
                if (date == null) date = bill.getBillDate().atStartOfDay();
                events.add(new Event(date, 4, bill.getId(), "CANCELLATION", bill, bill.getBillNumber(),
                        bill.getCancelReason(), money(netAmount(bill)).negate()));
            } else {
                currentDue += value(bill.getDueAmount());
                currentCredit += credit(bill);
            }
        }
        events.sort(Comparator.comparing(Event::date).thenComparing(Event::priority).thenComparing(Event::id));
        LocalDate start = from == null ? events.stream().map(e -> e.date().toLocalDate()).min(LocalDate::compareTo).orElse(end) : from;
        if (start.isAfter(end)) start = end;
        BigDecimal opening = BigDecimal.ZERO, balance = BigDecimal.ZERO, increases = BigDecimal.ZERO, decreases = BigDecimal.ZERO;
        List<SupplierStatementDTO.Entry> entries = new ArrayList<>();
        for (var event : events) {
            LocalDate date = event.date().toLocalDate();
            if (date.isAfter(end)) continue;
            balance = balance.add(event.delta());
            if (date.isBefore(start)) { opening = balance; continue; }
            BigDecimal increase = event.delta().max(BigDecimal.ZERO);
            BigDecimal decrease = event.delta().negate().max(BigDecimal.ZERO);
            increases = increases.add(increase);
            decreases = decreases.add(decrease);
            entries.add(new SupplierStatementDTO.Entry(event.date(), event.type(), event.bill().getId(), event.bill().getBillNumber(),
                    event.reference(), event.description(), increase.doubleValue(), decrease.doubleValue(), balance.doubleValue()));
        }
        return new SupplierStatementDTO(supplier.getId(), supplier.getName(), supplier.getSupplierCode(), supplier.getAddress(),
                supplier.getPhoneNumber(), start, end, opening.doubleValue(), balance.doubleValue(), increases.doubleValue(),
                decreases.doubleValue(), money(currentDue).doubleValue(), money(currentCredit).doubleValue(), entries);
    }
}
