package com.pahal.billingApp.service;


import com.pahal.billingApp.dto.AddBillPaymentRequest;
import com.pahal.billingApp.dto.CreateBillItemRequest;
import com.pahal.billingApp.dto.CreateBillPaymentRequest;
import com.pahal.billingApp.dto.CreateBillRequest;
import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.entity.BillItem;
import com.pahal.billingApp.entity.BillPayment;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.Salesman;
import com.pahal.billingApp.enums.PaymentMethod;
import com.pahal.billingApp.repository.BillRepository;
import com.pahal.billingApp.repository.BillPaymentRepository;
import com.pahal.billingApp.repository.ProductRepository;
import com.pahal.billingApp.repository.SalesManRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BillingService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private BillPaymentRepository billPaymentRepository;

    @Autowired
    private SalesManRepository salesManRepository;

    @Transactional(readOnly = true)
    public List<Bill> getAllBillsWithDetails() {
        List<Bill> bills = billRepository.findAllByOrderByCreatedAtDesc();
        hydratePayments(bills);
        return bills;
    }

    @Transactional(readOnly = true)
    public Bill getBillByIdWithDetails(Long id) {
        Bill bill = billRepository.findWithDetailsById(id)
                .orElseThrow(() -> new RuntimeException("Bill not found or access denied"));
        hydratePayments(List.of(bill));
        return bill;
    }

    @Transactional
    @CacheEvict(cacheNames = "reports", allEntries = true)
    public Bill createBill(CreateBillRequest request) {
        if (request == null) throw new RuntimeException("Request is required");

        Bill billRequest = new Bill();
        billRequest.setCustomerName(request.getCustomerName());
        billRequest.setContactInfo(request.getContactInfo());

        Salesman existingSalesMan = salesManRepository.findById(request.getSalesmanEmployeeId())
                .orElseThrow(() -> new RuntimeException("Salesman not found"));
        billRequest.setSalesMan(existingSalesMan);

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("At least one item is required");
        }

        List<BillItem> billItems = new ArrayList<>();
        for (CreateBillItemRequest itemReq : request.getItems()) {
            BillItem item = new BillItem();
            item.setProductName(itemReq.getProductName());
            item.setQuantity(itemReq.getQuantity());
            item.setDiscount(itemReq.getDiscount() != null ? itemReq.getDiscount() : 0.0);
            item.setUnitSellingPrice(itemReq.getUnitSellingPrice());
            billItems.add(item);
        }
        billRequest.setItems(billItems);

        if (request.getPayments() != null && !request.getPayments().isEmpty()) {
            List<BillPayment> payments = new ArrayList<>();
            for (CreateBillPaymentRequest pReq : request.getPayments()) {
                BillPayment p = new BillPayment();
                p.setMethod(pReq.getMethod());
                p.setAmount(pReq.getAmount());
                p.setReference(pReq.getReference());
                payments.add(p);
            }
            billRequest.setPayments(new LinkedHashSet<>(payments));
        }

        double taxableTotal = 0;

        for (BillItem item : billRequest.getItems()) {
            // 1. Find product
            Product product = productRepository.findByName(item.getProductName());
            if (product == null) throw new RuntimeException("Product not found: " + item.getProductName());

            // 2. Stock Check
            if (product.getStockQuantity() < item.getQuantity()) {
                throw new RuntimeException("Insufficient stock for: " + product.getName());
            }
            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());

            // 3. Calculation Fix: (Price - (Price * Disc / 100)) * Qty
            Double defaultPrice = product.getSellingPrice() != null ? product.getSellingPrice() : product.getPrice();
            double unitSellingPrice = item.getUnitSellingPrice() != null ? item.getUnitSellingPrice() : (defaultPrice != null ? defaultPrice : 0.0);
            item.setUnitSellingPrice(unitSellingPrice);
            // Keep priceAtSale as the actual unit price charged (backwards compatible with existing PDF/UI fields)
            item.setPriceAtSale(unitSellingPrice);

            double discountPct = item.getDiscount() != null ? item.getDiscount() : 0.0;
            double discountedPrice = unitSellingPrice - (unitSellingPrice * discountPct / 100);
            taxableTotal += (discountedPrice * item.getQuantity());
        }

        // 4. Apply 18% GST (9% CGST + 9% SGST) to match the Frontend
        double grandTotal = taxableTotal * 1.18;

        // Rounding to 2 decimal places before saving
        billRequest.setTotalAmount(Math.round(grandTotal * 100.0) / 100.0);

        applyPayments(billRequest);

        return billRepository.save(billRequest);
    }

    @Transactional
    @CacheEvict(cacheNames = "reports", allEntries = true)
    public Bill addDuePayment(Long billId, AddBillPaymentRequest request) {
        if (billId == null) throw new RuntimeException("Bill id is required");
        if (request == null) throw new RuntimeException("Request is required");
        if (request.getMethod() == null) throw new RuntimeException("Payment method is required");
        if (request.getMethod() == PaymentMethod.CREDIT) throw new RuntimeException("CREDIT cannot be used to collect due");
        if (request.getAmount() == null || request.getAmount() <= 0) throw new RuntimeException("Payment amount must be > 0");

        // Lock the bill row so concurrent "collect due" submissions (e.g., double-clicks)
        // cannot both observe the same due and record duplicate/over-collections.
        Bill bill = billRepository.findWithDetailsByIdForUpdate(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found or access denied"));

        double currentDue = bill.getDueAmount() != null ? bill.getDueAmount() : 0.0;
        double amount = request.getAmount();

        if (amount - currentDue > 0.0001) {
            throw new RuntimeException("Payment amount exceeds current due");
        }

        BillPayment payment = new BillPayment();
        payment.setBill(bill);
        payment.setMethod(request.getMethod());
        payment.setAmount(Math.round(amount * 100.0) / 100.0);
        payment.setReference(request.getReference());

        BillPayment creditAdjustment = new BillPayment();
        creditAdjustment.setBill(bill);
        creditAdjustment.setMethod(PaymentMethod.CREDIT);
        creditAdjustment.setAmount(Math.round((-amount) * 100.0) / 100.0);
        creditAdjustment.setReference(request.getReference());

        if (bill.getPayments() == null) {
            bill.setPayments(new LinkedHashSet<>());
        }
        bill.getPayments().add(payment);
        bill.getPayments().add(creditAdjustment);

        recomputePaidAndDue(bill);
        return billRepository.save(bill);
    }

    private void hydratePayments(Collection<Bill> bills) {
        if (bills == null || bills.isEmpty()) return;

        List<Long> billIds = bills.stream()
                .map(Bill::getId)
                .filter(id -> id != null)
                .toList();

        if (billIds.isEmpty()) return;

        Map<Long, LinkedHashSet<BillPayment>> paymentsByBillId = billPaymentRepository
                .findAllByBillIdInOrderByBillIdAscIdAsc(billIds)
                .stream()
                .collect(Collectors.groupingBy(
                        p -> p.getBill().getId(),
                        Collectors.toCollection(LinkedHashSet::new)
                ));

        for (Bill bill : bills) {
            if (bill == null || bill.getId() == null) continue;
            LinkedHashSet<BillPayment> payments = paymentsByBillId.getOrDefault(bill.getId(), new LinkedHashSet<>());
            bill.setPayments(payments);
        }
    }

    private void applyPayments(Bill billRequest) {
        Double total = billRequest.getTotalAmount() != null ? billRequest.getTotalAmount() : 0.0;

        List<BillPayment> incoming = billRequest.getPayments() == null
                ? new ArrayList<>()
                : new ArrayList<>(billRequest.getPayments());

        double sum = 0.0;
        for (BillPayment p : incoming) {
            if (p == null) continue;
            if (p.getMethod() == null) throw new RuntimeException("Payment method is required");
            if (p.getAmount() == null) throw new RuntimeException("Payment amount is required");
            if (p.getAmount() < 0 && p.getMethod() != PaymentMethod.CREDIT) {
                throw new RuntimeException("Payment amount must be >= 0");
            }
            sum += p.getAmount();
        }

        // If UI didn't send payments, default everything to CREDIT.
        if (incoming.isEmpty()) {
            BillPayment credit = new BillPayment();
            credit.setBill(billRequest);
            credit.setMethod(PaymentMethod.CREDIT);
            credit.setAmount(total);
            incoming.add(credit);
            billRequest.setPaidAmount(0.0);
            billRequest.setDueAmount(total);
            billRequest.setPayments(new LinkedHashSet<>(incoming));
            return;
        }

        if (sum - total > 0.0001) {
            throw new RuntimeException("Sum of payments exceeds bill total");
        }

        double remaining = total - sum;
        if (remaining > 0.0001) {
            // Auto-add CREDIT for the remaining balance unless the UI already provided it.
            BillPayment credit = new BillPayment();
            credit.setBill(billRequest);
            credit.setMethod(PaymentMethod.CREDIT);
            credit.setAmount(Math.round(remaining * 100.0) / 100.0);
            incoming.add(credit);
        }

        double due = 0.0;
        double paid = 0.0;
        for (BillPayment p : incoming) {
            if (p == null) continue;
            p.setBill(billRequest);
            double amount = p.getAmount() != null ? p.getAmount() : 0.0;
            if (p.getMethod() == PaymentMethod.CREDIT) {
                due += amount;
            } else {
                paid += amount;
            }
        }

        billRequest.setPaidAmount(Math.round(paid * 100.0) / 100.0);
        billRequest.setDueAmount(Math.round(due * 100.0) / 100.0);
        billRequest.setPayments(new LinkedHashSet<>(incoming));
    }

    private void recomputePaidAndDue(Bill bill) {
        double due = 0.0;
        double paid = 0.0;
        if (bill.getPayments() != null) {
            for (BillPayment p : bill.getPayments()) {
                if (p == null || p.getMethod() == null) continue;
                double amount = p.getAmount() != null ? p.getAmount() : 0.0;
                if (p.getMethod() == PaymentMethod.CREDIT) {
                    due += amount;
                } else {
                    paid += amount;
                }
            }
        }
        bill.setPaidAmount(Math.round(paid * 100.0) / 100.0);
        bill.setDueAmount(Math.round(due * 100.0) / 100.0);
    }
}
