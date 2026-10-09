package com.pahal.billingApp.service;

import com.pahal.billingApp.entity.PurchaseBill;
import com.pahal.billingApp.entity.PurchaseBillItem;
import com.pahal.billingApp.enums.PurchaseStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;

/** All credit allocation is calculated in decimal, rounded only at cumulative boundaries. */
public final class PurchaseAmounts {
    private PurchaseAmounts() {}
    public static double value(Double value) { return value == null ? 0 : value; }
    public static BigDecimal money(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Amount must be finite.");
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
    public static double netAmount(PurchaseBill bill) {
        return money(value(bill.getTotalAmount()) - value(bill.getReturnedAmount())).doubleValue();
    }
    public static double credit(PurchaseBill bill) {
        if (bill.getStatus() == PurchaseStatus.CANCELLED) return 0;
        return money(Math.max(0, value(bill.getPaidAmount()) - value(bill.getRefundedAmount()) - netAmount(bill))).doubleValue();
    }
    public static void updateDue(PurchaseBill bill) {
        bill.setDueAmount(bill.getStatus() == PurchaseStatus.CANCELLED ? 0 : money(Math.max(0,
                netAmount(bill) - value(bill.getPaidAmount()) + value(bill.getRefundedAmount()))).doubleValue());
    }
    public static String returnStatus(PurchaseBill bill) {
        boolean any = bill.getItems().stream().anyMatch(i -> value(i.getReturnedQuantity()) > 0);
        if (!any) return "NONE";
        return bill.getItems().stream().allMatch(i -> value(i.getReturnedQuantity()) >= value(i.getQuantity())) ? "FULL" : "PARTIAL";
    }
    /** Allocate bill discount and tax by line value; zero-value bills fall back to quantity. */
    public static BigDecimal cumulativeCredit(PurchaseBill bill, PurchaseBillItem target, double returnedQuantity) {
        if (target.getTaxableAmount() != null) {
            BigDecimal result = BigDecimal.ZERO;
            for (Double component : new Double[]{target.getTaxableAmount(), target.getCgstAmount(), target.getSgstAmount(), target.getIgstAmount()}) {
                result = result.add(money(value(component)).multiply(BigDecimal.valueOf(returnedQuantity))
                        .divide(BigDecimal.valueOf(value(target.getQuantity())), 2, RoundingMode.HALF_UP));
            }
            return result;
        }
        var items = bill.getItems().stream().sorted(Comparator.comparing(PurchaseBillItem::getId)).toList();
        BigDecimal subtotal = items.stream().map(i -> money(value(i.getLineTotal()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean byQuantity = subtotal.signum() == 0;
        BigDecimal weightTotal = byQuantity ? items.stream().map(i -> BigDecimal.valueOf(value(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add) : subtotal;
        if (weightTotal.signum() <= 0) throw new IllegalArgumentException("Purchase has no valid quantity to return.");
        BigDecimal total = money(value(bill.getTotalAmount()));
        BigDecimal allocated = BigDecimal.ZERO;
        for (int index = 0; index < items.size(); index++) {
            var item = items.get(index);
            BigDecimal weight = byQuantity ? BigDecimal.valueOf(value(item.getQuantity())) : money(value(item.getLineTotal()));
            BigDecimal share = index == items.size() - 1 ? total.subtract(allocated) :
                    total.multiply(weight).divide(weightTotal, 2, RoundingMode.HALF_UP);
            // Cap early rounded shares to avoid a negative residual on invoices with many tiny lines.
            share = share.min(total.subtract(allocated)).max(BigDecimal.ZERO);
            allocated = allocated.add(share);
            if (item.getId().equals(target.getId())) {
                return share.multiply(BigDecimal.valueOf(returnedQuantity))
                        .divide(BigDecimal.valueOf(value(item.getQuantity())), 2, RoundingMode.HALF_UP);
            }
        }
        throw new IllegalArgumentException("Purchase item not found.");
    }
}
