package com.pahal.billingApp.service;

import com.pahal.billingApp.dto.PurchaseReturnDTO;
import com.pahal.billingApp.entity.*;
import com.pahal.billingApp.enums.PaymentMethod;
import com.pahal.billingApp.enums.PurchaseStatus;
import com.pahal.billingApp.enums.StockMovementType;
import com.pahal.billingApp.repository.PurchaseBillRepository;
import com.pahal.billingApp.security.CustomUserDetails;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import static com.pahal.billingApp.service.PurchaseAmounts.*;

@Service
public class PurchaseReturnService {
    private final PurchaseBillRepository bills;
    private final StockService stock;
    private final GstService gst;
    public PurchaseReturnService(PurchaseBillRepository bills, StockService stock, GstService gst) {
        this.bills = bills;
        this.stock = stock;
        this.gst = gst;
    }

    @Transactional
    public PurchaseBill returnItems(Long id, PurchaseReturnDTO.Request request) {
        if (request == null) throw invalid("Return details are required.");
        String key = requestKey(request.requestKey());
        String reason = requiredText(request.reason(), 500, "Return reason");
        if (request.items() == null || request.items().isEmpty() || request.items().size() > 1000) {
            throw invalid("Select between 1 and 1,000 purchase lines to return.");
        }
        var lines = new TreeMap<Long, PurchaseReturnDTO.Line>();
        StringBuilder payload = new StringBuilder(reason);
        for (var line : request.items()) {
            if (line == null || line.purchaseItemId() == null || line.purchaseItemId() <= 0
                    || lines.putIfAbsent(line.purchaseItemId(), line) != null) {
                throw invalid("Each purchase item must appear once in a return.");
            }
            StockService.validateQuantity(line.quantity(), false);
            StockService.validateQuantity(line.expectedReturnedQuantity(), true);
        }
        lines.values().forEach(l -> payload.append('|').append(l.purchaseItemId()).append(':')
                .append(l.quantity()).append(':').append(l.expectedReturnedQuantity()));
        String fingerprint = fingerprint(payload.toString());
        PurchaseBill bill = lockBill(id);
        var previous = bill.getReturns().stream().filter(r -> key.equals(r.getRequestKey())).findFirst();
        if (previous.isPresent()) {
            requireMatching(previous.get().getFingerprint(), fingerprint);
            return hydrate(bill);
        }
        if (bill.getStatus() == PurchaseStatus.CANCELLED) throw invalid("Cannot return a cancelled purchase.");
        Map<Long, PurchaseBillItem> items = new HashMap<>();
        bill.getItems().forEach(i -> items.put(i.getId(), i));
        for (var line : lines.values()) {
            PurchaseBillItem item = items.get(line.purchaseItemId());
            if (item == null) throw invalid("Return item does not belong to this purchase.");
            double returned = value(item.getReturnedQuantity());
            if (money(returned).compareTo(money(line.expectedReturnedQuantity())) != 0) {
                throw invalid("Return quantities have changed. Refresh the purchase and try again.");
            }
            if (StockService.round2(returned + line.quantity()) > value(item.getQuantity())) {
                throw invalid("Return quantity exceeds remaining purchased quantity for " + item.getProductName() + ".");
            }
        }
        lines.keySet().stream().map(i -> items.get(i).getProduct().getId()).distinct().sorted().forEach(stock::lockProduct);
        PurchaseReturn document = new PurchaseReturn();
        document.setPurchaseBill(bill);
        document.setRequestKey(key);
        document.setFingerprint(fingerprint);
        document.setReason(reason);
        document.setCreatedAt(LocalDateTime.now());
        document.setActorName(actorName());
        document.setActorUserId(limitActor(actorId()));
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (var line : lines.values()) {
            PurchaseBillItem item = items.get(line.purchaseItemId());
            double before = value(item.getReturnedQuantity());
            double after = StockService.round2(before + line.quantity());
            BigDecimal credit = cumulativeCredit(bill, item, after).subtract(cumulativeCredit(bill, item, before));
            stock.changeStock(item.getProduct(), -line.quantity(), StockMovementType.PURCHASE_RETURN,
                    "Purchase return", reason, "Return: " + bill.getBillNumber(), bill.getId());
            PurchaseReturn.Item snapshot = new PurchaseReturn.Item();
            snapshot.setPurchaseItemId(item.getId());
            snapshot.setProductId(item.getProduct().getId());
            snapshot.setProductName(item.getProductName());
            snapshot.setQuantity(line.quantity());
            snapshot.setCreditAmount(credit.doubleValue());
            document.getItems().add(snapshot);
            item.setReturnedQuantity(after);
            totalCredit = totalCredit.add(credit);
        }
        document.setCreditAmount(totalCredit.doubleValue());
        bill.getReturns().add(document);
        bill.setReturnedAmount(money(value(bill.getReturnedAmount())).add(totalCredit).doubleValue());
        updateDue(bill);
        PurchaseBill saved = bills.saveAndFlush(bill);
        gst.purchaseReturn(saved, document);
        return hydrate(saved);
    }

    @Transactional
    public PurchaseBill recordRefund(Long id, PurchaseReturnDTO.RefundRequest request) {
        if (request == null) throw invalid("Refund details are required.");
        String key = requestKey(request.requestKey());
        double amount = amount(request.amount(), false);
        amount(request.expectedCreditAmount(), true);
        if (request.method() == null || request.method() == PaymentMethod.CREDIT) {
            throw invalid("Choose how the supplier actually refunded the money.");
        }
        String reference = request.reference() == null ? null : request.reference().trim();
        if (reference != null && reference.length() > 255) throw invalid("Refund reference is too long.");
        String fingerprint = fingerprint(amount + "|" + request.expectedCreditAmount() + "|" + request.method() + "|" + reference);
        PurchaseBill bill = lockBill(id);
        var previous = bill.getPayments().stream().filter(p -> Boolean.TRUE.equals(p.getRefund()) && key.equals(p.getRequestKey())).findFirst();
        if (previous.isPresent()) {
            requireMatching(previous.get().getFingerprint(), fingerprint);
            return hydrate(bill);
        }
        if (bill.getStatus() == PurchaseStatus.CANCELLED) throw invalid("Cannot refund a cancelled purchase.");
        double available = credit(bill);
        if (money(available).compareTo(money(request.expectedCreditAmount())) != 0) {
            throw invalid("Supplier credit has changed. Refresh the purchase and try again.");
        }
        if (amount > available) throw invalid("Refund exceeds this purchase's available supplier credit.");
        PurchasePayment refund = new PurchasePayment();
        refund.setPurchaseBill(bill);
        refund.setRefund(true);
        refund.setAmount(amount);
        refund.setMethod(request.method());
        refund.setReference(reference);
        refund.setRequestKey(key);
        refund.setFingerprint(fingerprint);
        refund.setActorName(actorName());
        bill.getPayments().add(refund);
        bill.setRefundedAmount(money(value(bill.getRefundedAmount()) + amount).doubleValue());
        updateDue(bill);
        return hydrate(bills.saveAndFlush(bill));
    }

    private PurchaseBill lockBill(Long id) {
        if (id == null || id <= 0) throw invalid("Purchase bill id is required.");
        return bills.findScopedForUpdate(id, StockService.requireTenant()).orElseThrow(() -> invalid("Purchase bill not found."));
    }
    public static PurchaseBill hydrate(PurchaseBill bill) {
        bill.getSupplier().getName();
        bill.getItems().forEach(i -> i.getProduct().getId());
        bill.getPayments().size();
        bill.getReturns().forEach(r -> r.getItems().size());
        return bill;
    }
    public static double amount(Double value, boolean allowZero) {
        if (value == null || !Double.isFinite(value) || value < 0 || (!allowZero && value == 0)
                || value > 1_000_000_000d || BigDecimal.valueOf(value).stripTrailingZeros().scale() > 2) {
            throw invalid("Amount must be " + (allowZero ? "zero or positive" : "positive") + ", with at most two decimal places.");
        }
        return money(value).doubleValue();
    }
    private static String requestKey(String key) {
        try { return UUID.fromString(key).toString(); }
        catch (Exception e) { throw invalid("A UUID request key is required to prevent duplicate submissions."); }
    }
    private static String requiredText(String text, int max, String name) {
        if (text == null || text.isBlank() || text.trim().length() > max) throw invalid(name + " is required (maximum " + max + " characters).");
        return text.trim();
    }
    private static String fingerprint(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static void requireMatching(String previous, String current) {
        if (!current.equals(previous)) throw invalid("This request key has already been used with different details.");
    }
    private static String actorName() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) throw invalid("A signed-in user is required.");
        var principal = authentication.getPrincipal();
        return limitActor(principal instanceof CustomUserDetails u ? u.getDisplayName() : authentication.getName());
    }
    private static String limitActor(String value) { return value.length() <= 160 ? value : value.substring(0, 160); }
    private static String actorId() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
