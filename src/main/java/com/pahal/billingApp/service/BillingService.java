package com.pahal.billingApp.service;


import com.pahal.billingApp.dto.AddBillPaymentRequest;
import com.pahal.billingApp.dto.BillRegisterResponse;
import com.pahal.billingApp.dto.BillRegisterSummaryResponse;
import com.pahal.billingApp.dto.CancelBillRequest;
import com.pahal.billingApp.dto.CreateBillItemRequest;
import com.pahal.billingApp.dto.CreateBillPaymentRequest;
import com.pahal.billingApp.dto.CreateBillRequest;
import com.pahal.billingApp.dto.ReturnBillItemRequest;
import com.pahal.billingApp.dto.ReturnBillRequest;
import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.entity.CashierShift;
import com.pahal.billingApp.entity.BillItem;
import com.pahal.billingApp.entity.BillPayment;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.StockMovement;
import com.pahal.billingApp.entity.ProductBarcode;
import com.pahal.billingApp.entity.Salesman;
import com.pahal.billingApp.enums.BillStatus;
import com.pahal.billingApp.enums.PaymentMethod;
import com.pahal.billingApp.enums.StockMovementType;
import com.pahal.billingApp.repository.BillRepository;
import com.pahal.billingApp.repository.BillPaymentRepository;
import com.pahal.billingApp.repository.ProductBarcodeRepository;
import com.pahal.billingApp.repository.ProductRepository;
import com.pahal.billingApp.repository.SalesManRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
public class BillingService {

    @Autowired
    private CashierShiftService shifts;

    @Autowired
    private StockService stockService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductBarcodeRepository productBarcodeRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private BillPaymentRepository billPaymentRepository;

    @Autowired
    private SalesManRepository salesManRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private BillSubmissionService submissions;
    @Autowired private GstService gst;
    @Autowired private com.pahal.billingApp.licensing.ModuleAccessService moduleAccess;
    @Autowired private com.pahal.billingApp.licensing.TenantLicenseService tenantLicenses;
    @Autowired private com.pahal.billingApp.licensing.CounterService licensedCounters;

    @PersistenceContext
    private EntityManager entityManager;

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

    @Transactional(readOnly = true)
    public BillRegisterResponse getBillRegister(
            LocalDate from,
            LocalDate to,
            String billNo,
            String customer,
            String phone,
            String salesmanId,
            PaymentMethod paymentMethod,
            boolean dueOnly,
            int page,
            int size) {
        LocalDateTime start = normalizeFrom(from);
        LocalDateTime end = normalizeTo(to);
        Long billId = parseBillId(billNo);

        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Bill> billPage = findBillRegisterPage(
                start,
                end,
                billId,
                blankToNull(customer),
                blankToNull(phone),
                blankToNull(salesmanId),
                paymentMethod,
                dueOnly,
                pageRequest
        );
        hydratePayments(billPage.getContent());

        BillRegisterResponse response = new BillRegisterResponse();
        response.setItems(billPage.getContent().stream().map(this::toBillRegisterItem).toList());
        response.setPage(billPage.getNumber());
        response.setSize(billPage.getSize());
        response.setTotalElements(billPage.getTotalElements());
        response.setTotalPages(billPage.getTotalPages());
        return response;
    }

    @Transactional(readOnly = true)
    public BillRegisterSummaryResponse getBillRegisterSummary(
            LocalDate from,
            LocalDate to,
            String billNo,
            String customer,
            String phone,
            String salesmanId,
            PaymentMethod paymentMethod,
            boolean dueOnly) {
        List<Bill> bills = findBillRegisterForSummary(
                normalizeFrom(from),
                normalizeTo(to),
                parseBillId(billNo),
                blankToNull(customer),
                blankToNull(phone),
                blankToNull(salesmanId),
                paymentMethod,
                dueOnly
        );
        hydratePayments(bills);

        BillRegisterSummaryResponse response = new BillRegisterSummaryResponse();
        response.setTotalBills(bills.size());
        response.setTotalSales(round2(bills.stream().mapToDouble(b -> nonNull(b.getTotalAmount())).sum()));
        response.setTotalPaid(round2(bills.stream().mapToDouble(b -> nonNull(b.getPaidAmount())).sum()));
        response.setTotalDue(round2(bills.stream().mapToDouble(b -> nonNull(b.getDueAmount())).sum()));

        Map<PaymentMethod, Double> split = new LinkedHashMap<>();
        for (PaymentMethod method : PaymentMethod.values()) {
            split.put(method, 0.0);
        }
        for (Bill bill : bills) {
            if (bill.getPayments() == null) continue;
            for (BillPayment payment : bill.getPayments()) {
                if (payment == null || payment.getMethod() == null) continue;
                split.merge(payment.getMethod(), nonNull(payment.getAmount()), Double::sum);
            }
        }
        split.replaceAll((method, amount) -> round2(amount));
        response.setPaymentSplit(split);
        return response;
    }

    private Page<Bill> findBillRegisterPage(
            LocalDateTime start,
            LocalDateTime end,
            Long billId,
            String customer,
            String phone,
            String salesmanId,
            PaymentMethod paymentMethod,
            boolean dueOnly,
            PageRequest pageRequest) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Bill> query = cb.createQuery(Bill.class);
        Root<Bill> root = query.from(Bill.class);
        root.fetch("salesMan", JoinType.LEFT);
        query.select(root).distinct(true);
        query.where(buildBillRegisterPredicates(cb, query, root, start, end, billId, customer, phone, salesmanId, paymentMethod, dueOnly));
        query.orderBy(cb.desc(root.get("createdAt")));

        TypedQuery<Bill> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageRequest.getOffset());
        typedQuery.setMaxResults(pageRequest.getPageSize());

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Bill> countRoot = countQuery.from(Bill.class);
        countQuery.select(cb.countDistinct(countRoot));
        countQuery.where(buildBillRegisterPredicates(cb, countQuery, countRoot, start, end, billId, customer, phone, salesmanId, paymentMethod, dueOnly));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(typedQuery.getResultList(), pageRequest, total);
    }

    private List<Bill> findBillRegisterForSummary(
            LocalDateTime start,
            LocalDateTime end,
            Long billId,
            String customer,
            String phone,
            String salesmanId,
            PaymentMethod paymentMethod,
            boolean dueOnly) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Bill> query = cb.createQuery(Bill.class);
        Root<Bill> root = query.from(Bill.class);
        root.fetch("salesMan", JoinType.LEFT);
        query.select(root).distinct(true);
        query.where(buildBillRegisterPredicates(cb, query, root, start, end, billId, customer, phone, salesmanId, paymentMethod, dueOnly));
        query.orderBy(cb.desc(root.get("createdAt")));
        return entityManager.createQuery(query).getResultList();
    }

    private Predicate[] buildBillRegisterPredicates(
            CriteriaBuilder cb,
            CriteriaQuery<?> query,
            Root<Bill> root,
            LocalDateTime start,
            LocalDateTime end,
            Long billId,
            String customer,
            String phone,
            String salesmanId,
            PaymentMethod paymentMethod,
            boolean dueOnly) {
        List<Predicate> predicates = new ArrayList<>();

        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null) {
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
        }

        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
        predicates.add(cb.lessThan(root.get("createdAt"), end));

        if (billId != null) {
            predicates.add(cb.equal(root.get("id"), billId));
        }
        if (customer != null) {
            predicates.add(cb.like(cb.lower(root.get("customerName")), "%" + customer.toLowerCase() + "%"));
        }
        if (phone != null) {
            predicates.add(cb.like(cb.lower(root.get("contactInfo")), "%" + phone.toLowerCase() + "%"));
        }
        if (salesmanId != null) {
            Join<Object, Object> salesMan = root.join("salesMan", JoinType.LEFT);
            predicates.add(cb.equal(salesMan.get("employeeId"), salesmanId));
        }
        if (dueOnly) {
            predicates.add(cb.greaterThan(cb.coalesce(root.get("dueAmount"), 0.0), 0.0));
        }
        if (paymentMethod != null) {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<BillPayment> payment = subquery.from(BillPayment.class);
            subquery.select(payment.get("id"));
            subquery.where(
                    cb.equal(payment.get("bill"), root),
                    cb.equal(payment.get("method"), paymentMethod)
            );
            predicates.add(cb.exists(subquery));
        }

        return predicates.toArray(new Predicate[0]);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(cacheNames = "reports", allEntries = true)
    public Bill createBill(CreateBillRequest request) {
        return createBill(request, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(cacheNames = "reports", allEntries = true)
    public Bill createBill(CreateBillRequest request, String cashierName) {
        if (request == null) throw new IllegalArgumentException("Request is required");

        var submission = submissions.begin(request);
        if (submission.existing() != null) {
            // Initialize the managed collection without replacing it in this write transaction.
            submission.existing().getPayments().size();
            return submission.existing();
        }

        tenantLicenses.lockTenant(moduleAccess.tenant());
        moduleAccess.require(com.pahal.billingApp.licensing.Feature.BILLING);
        licensedCounters.requireForNewTransaction();
        CashierShift shift = shifts.requireOpenForPosting();
        licensedCounters.requireForBill(shift);
        Bill billRequest = new Bill();
        billRequest.setCreationRequestKey(submission.key());
        billRequest.setCreationFingerprint(submission.fingerprint());
        billRequest.setShiftId(shift.getId());
        billRequest.setCashierUserId(shift.getCashierUserId());
        billRequest.setCustomerName(request.getCustomerName());
        billRequest.setContactInfo(request.getContactInfo());
        billRequest.setCashierName(blankToNull(cashierName));

        Salesman existingSalesMan = salesManRepository.findById(request.getSalesmanEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Salesman not found"));
        billRequest.setSalesMan(existingSalesMan);

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("At least one item is required");
        }
        customerService.ensureBillCustomer(request.getCustomerName(), request.getContactInfo());

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
        double gstTotal = 0;

        List<BillItem> billItems = new ArrayList<>();
        List<ProductResolution> resolutions = request.getItems().stream().map(this::resolveProduct).toList();
        resolutions.stream().map(resolution -> resolution.product().getId()).distinct().sorted().forEach(stockService::lockProduct);
        List<StockMovement> stockMovements = new ArrayList<>();
        for (int index = 0; index < request.getItems().size(); index++) {
            CreateBillItemRequest itemReq = request.getItems().get(index);
            ProductResolution resolution = resolutions.get(index);
            Product product = resolution.product();
            double requestedQuantity = normalizeRequestedQuantity(itemReq.getQuantity());
            double effectiveQuantity = round2(requestedQuantity * resolution.quantityPerScan());

            BillItem item = new BillItem();
            item.setProductId(product.getId());
            item.setBarcode(resolution.barcodeUsed());
            item.setProductName(product.getName());
            item.setQuantity(effectiveQuantity);
            Double currentCost = product.getCostPrice();
            // The product has been locked/refreshed above. Never derive historical cost at report time.
            if (currentCost != null && Double.isFinite(currentCost) && currentCost >= 0) {
                item.setUnitCostAtSale(java.math.BigDecimal.valueOf(currentCost)
                        .setScale(6, java.math.RoundingMode.HALF_UP));
            }
            item.setDiscount(itemReq.getDiscount() != null ? itemReq.getDiscount() : 0.0);
            item.setUnitSellingPrice(itemReq.getUnitSellingPrice());

            stockMovements.add(stockService.changeStock(product, -effectiveQuantity,
                    StockMovementType.SALE, "Sale", null, null, null));

            Double defaultPrice = product.getSellingPrice() != null ? product.getSellingPrice() : product.getPrice();
            double unitSellingPrice = item.getUnitSellingPrice() != null ? item.getUnitSellingPrice() : (defaultPrice != null ? defaultPrice : 0.0);
            item.setUnitSellingPrice(unitSellingPrice);
            // Keep priceAtSale as the actual unit price charged (backwards compatible with existing PDF/UI fields)
            item.setPriceAtSale(unitSellingPrice);

            double discountPct = item.getDiscount() != null ? item.getDiscount() : 0.0;
            double discountedPrice = unitSellingPrice - (unitSellingPrice * discountPct / 100);
            double lineTaxable = round2(discountedPrice * item.getQuantity());
            double lineGstRate = product.getGstRate() != null ? product.getGstRate() : 0.0;
            double lineGstAmount = round2(lineTaxable * lineGstRate);

            item.setHsnCode(product.getHsnCode());
            item.setGstRate(lineGstRate);
            item.setTaxableAmount(lineTaxable);
            item.setGstAmount(lineGstAmount);

            taxableTotal += lineTaxable;
            gstTotal += lineGstAmount;
            billItems.add(item);
        }
        billRequest.setItems(billItems);

        double subTotal = round2(taxableTotal);
        double gstAmount = round2(gstTotal);
        double grandTotal = round2(subTotal + gstAmount);

        double instantDiscount = request.getInstantDiscountAmount() != null ? request.getInstantDiscountAmount() : 0.0;
        if (instantDiscount < 0.0) {
            throw new IllegalArgumentException("Instant discount must be >= 0");
        }
        if (instantDiscount - grandTotal > 0.0001) {
            throw new IllegalArgumentException("Instant discount cannot exceed bill total");
        }
        if (instantDiscount > 0.0001) {
            grandTotal = round2(grandTotal - instantDiscount);
        }

        billRequest.setSubTotalAmount(subTotal);
        billRequest.setGstApplied(gstAmount > 0.0001);
        billRequest.setGstRate(0.0);
        billRequest.setGstAmount(gstAmount);
        billRequest.setInstantDiscountAmount(instantDiscount > 0.0001 ? round2(instantDiscount) : 0.0);
        billRequest.setTotalAmount(grandTotal);

        gst.prepareSale(billRequest, request);

        applyPayments(billRequest);

        shifts.assignNewPayments(shift, billRequest);
        Bill savedBill = billRepository.save(billRequest);
        gst.postSale(savedBill);
        for (var movement : stockMovements) {
            movement.setReferenceId(savedBill.getId());
            movement.setReference(billNumber(savedBill.getId()));
        }
        return savedBill;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public java.util.Optional<Bill> findSavedSubmission(String key) {
        var existing = submissions.findForCurrentCashier(key);
        existing.ifPresent(bill -> bill.getPayments().size());
        return existing;
    }

    private ProductResolution resolveProduct(CreateBillItemRequest itemReq) {
        if (itemReq == null) {
            throw new IllegalArgumentException("Bill item is required");
        }

        String barcode = blankToNull(itemReq.getBarcode());
        if (barcode != null) {
            ProductBarcode linkedBarcode = productBarcodeRepository.findByBarcode(barcode).orElse(null);
            if (linkedBarcode != null) {
                Product product = linkedBarcode.getProduct();
                assertRequestedProductMatchesBarcode(itemReq.getProductId(), product);
                return new ProductResolution(
                        product,
                        linkedBarcode.getBarcode(),
                        normalizeQuantityPerScan(linkedBarcode.getQuantityPerScan()));
            }

            Product product = productRepository.findByBarcode(barcode);
            if (product == null) {
                throw new IllegalArgumentException("Product not found for barcode: " + barcode);
            }
            assertRequestedProductMatchesBarcode(itemReq.getProductId(), product);
            return new ProductResolution(product, barcode, 1.0);
        }

        if (itemReq.getProductId() != null) {
            Product product = findProduct(itemReq.getProductId());
            return new ProductResolution(product, null, 1.0);
        }

        String productName = blankToNull(itemReq.getProductName());
        if (productName == null) {
            throw new IllegalArgumentException("Product id, barcode, or product name is required");
        }

        Product product = productRepository.findByName(productName);
        if (product == null) {
            throw new IllegalArgumentException("Product not found: " + productName);
        }
        return new ProductResolution(product, null, 1.0);
    }

    private Product findProduct(Long productId) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            return productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        }
        return productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    private void assertRequestedProductMatchesBarcode(Long requestedProductId, Product product) {
        if (requestedProductId == null || product == null || product.getId() == null) {
            return;
        }
        if (!requestedProductId.equals(product.getId())) {
            throw new IllegalArgumentException("Barcode does not belong to requested product");
        }
    }

    private static double normalizeRequestedQuantity(Double quantity) {
        if (quantity == null || quantity <= 0.0) {
            throw new IllegalArgumentException("Quantity must be > 0");
        }
        return quantity;
    }

    private static double normalizeQuantityPerScan(Double quantityPerScan) {
        if (quantityPerScan == null) {
            return 1.0;
        }
        if (quantityPerScan <= 0.0) {
            throw new IllegalArgumentException("Quantity per scan must be > 0");
        }
        return quantityPerScan;
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
        CashierShift shift = shifts.requireOpenForPosting();
        Bill bill = billRepository.findWithDetailsByIdForUpdate(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found or access denied"));

        if (getEffectiveStatus(bill) == BillStatus.CANCELLED) {
            throw new RuntimeException("Cannot add payment to a cancelled bill");
        }

        double currentDue = bill.getDueAmount() != null ? bill.getDueAmount() : 0.0;
        double amount = CashierShiftService.validatePaymentAmount(request.getAmount());

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
        shifts.assignNewPayments(shift, bill);
        return billRepository.save(bill);
    }

    @Transactional
    @CacheEvict(cacheNames = "reports", allEntries = true)
    public Bill cancelBill(Long billId, CancelBillRequest request) {
        if (billId == null) throw new RuntimeException("Bill id is required");

        CashierShift shift = shifts.requireOpenForPosting();
        Bill bill = billRepository.findWithDetailsByIdForUpdate(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found or access denied"));

        if (getEffectiveStatus(bill) == BillStatus.CANCELLED) {
            throw new RuntimeException("Bill is already cancelled");
        }

        Map<Long, Double> previousReturns = new LinkedHashMap<>();
        distinctBillItems(bill).forEach(item -> previousReturns.put(item.getId(), nonNull(item.getReturnedQuantity())));

        if (bill.getItems() != null) {
            distinctBillItems(bill).stream().map(BillItem::getProductId).distinct().sorted().forEach(stockService::lockProduct);
            for (BillItem item : distinctBillItems(bill)) {
                double netQuantity = netQuantity(item);
                if (netQuantity <= 0.0001) continue;
                Product product = findProduct(item.getProductId());
                stockService.changeStock(product, netQuantity, StockMovementType.SALE_CANCELLED,
                        "Sale cancelled", request == null ? null : request.getReason(), billNumber(bill.getId()), bill.getId());
                item.setReturnedQuantity(round2(nonNull(item.getReturnedQuantity()) + netQuantity));
            }
        }

        gst.saleReturn(bill, previousReturns, request == null ? "Cancellation" : request.getReason());
        bill.setSubTotalAmount(0.0);
        bill.setGstApplied(false);
        bill.setGstAmount(0.0);
        bill.setInstantDiscountAmount(0.0);
        bill.setTotalAmount(0.0);
        adjustPaymentsToTotal(bill, 0.0, "Bill cancelled");
        bill.setStatus(BillStatus.CANCELLED);
        bill.setCancelReason(request != null ? request.getReason() : null);
        bill.setCancelledAt(LocalDateTime.now());
        shifts.assignNewPayments(shift, bill);
        return billRepository.save(bill);
    }

    @Transactional
    @CacheEvict(cacheNames = "reports", allEntries = true)
    public Bill returnBillItems(Long billId, ReturnBillRequest request) {
        if (billId == null) throw new RuntimeException("Bill id is required");
        if (request == null) throw new RuntimeException("Request is required");
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("At least one return item is required");
        }

        CashierShift shift = shifts.requireOpenForPosting();
        Bill bill = billRepository.findWithDetailsByIdForUpdate(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found or access denied"));

        if (getEffectiveStatus(bill) == BillStatus.CANCELLED) {
            throw new RuntimeException("Cannot return items from a cancelled bill");
        }

        Map<Long, Double> previousReturns = new LinkedHashMap<>();
        distinctBillItems(bill).forEach(item -> previousReturns.put(item.getId(), nonNull(item.getReturnedQuantity())));
        distinctBillItems(bill).stream().map(BillItem::getProductId).distinct().sorted().forEach(stockService::lockProduct);
        for (ReturnBillItemRequest returnItem : request.getItems()) {
            if (returnItem == null || returnItem.getQuantity() == null || returnItem.getQuantity() <= 0.0) {
                throw new RuntimeException("Return quantity must be > 0");
            }
            BillItem billItem = resolveReturnItem(bill, returnItem);
            double quantity = round2(returnItem.getQuantity());
            if (quantity - netQuantity(billItem) > 0.0001) {
                throw new RuntimeException("Return quantity exceeds sold quantity for: " + billItem.getProductName());
            }
            Product product = findProduct(billItem.getProductId());
            stockService.changeStock(product, quantity, StockMovementType.SALE_RETURN,
                    "Customer return", request.getReason(), billNumber(bill.getId()), bill.getId());
            billItem.setReturnedQuantity(round2(nonNull(billItem.getReturnedQuantity()) + quantity));
        }

        gst.saleReturn(bill, previousReturns, request.getReason());
        recalculateBillTotalsFromNetItems(bill);
        adjustPaymentsToTotal(bill, nonNull(bill.getTotalAmount()), "Bill return");
        bill.setReturnReason(request.getReason());
        bill.setLastReturnedAt(LocalDateTime.now());
        bill.setStatus(allItemsReturned(bill) ? BillStatus.CANCELLED : BillStatus.PARTIALLY_RETURNED);
        if (bill.getStatus() == BillStatus.CANCELLED) {
            bill.setCancelReason(request.getReason());
            bill.setCancelledAt(LocalDateTime.now());
        }
        shifts.assignNewPayments(shift, bill);
        return billRepository.save(bill);
    }

    private BillItem resolveReturnItem(Bill bill, ReturnBillItemRequest request) {
        if (bill.getItems() == null) throw new RuntimeException("Bill has no items");
        return distinctBillItems(bill).stream()
                .filter(item -> matchesReturnItem(item, request))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Return item does not belong to bill"));
    }

    private boolean matchesReturnItem(BillItem item, ReturnBillItemRequest request) {
        if (request.getBillItemId() != null) return request.getBillItemId().equals(item.getId());
        if (request.getProductId() != null) return request.getProductId().equals(item.getProductId());
        String barcode = blankToNull(request.getBarcode());
        return barcode != null && barcode.equals(item.getBarcode());
    }

    private void recalculateBillTotalsFromNetItems(Bill bill) {
        if (bill.getTaxRegistrationMode() != null) {
            double taxable = 0, tax = 0, discount = 0;
            for (BillItem item : distinctBillItems(bill)) {
                taxable += remainingTaxValue(item.getTaxableAmount(), item);
                tax += remainingTaxValue(item.getCgstAmount(), item) + remainingTaxValue(item.getSgstAmount(), item) + remainingTaxValue(item.getIgstAmount(), item);
                discount += remainingTaxValue(item.getFinalDiscountAmount(), item);
            }
            bill.setSubTotalAmount(round2(taxable)); bill.setGstAmount(round2(tax)); bill.setGstApplied(tax > 0);
            bill.setInstantDiscountAmount(round2(discount)); bill.setTotalAmount(round2(taxable + tax));
            return;
        }
        double taxableTotal = 0.0;
        double gstTotal = 0.0;
        if (bill.getItems() != null) {
            for (BillItem item : distinctBillItems(bill)) {
                double originalQuantity = nonNull(item.getQuantity());
                double netQuantity = netQuantity(item);
                double ratio = originalQuantity > 0.0001 ? netQuantity / originalQuantity : 0.0;
                taxableTotal += round2(nonNull(item.getTaxableAmount()) * ratio);
                gstTotal += round2(nonNull(item.getGstAmount()) * ratio);
            }
        }
        double subTotal = round2(taxableTotal);
        double gstAmount = round2(gstTotal);
        double totalBeforeInstantDiscount = round2(subTotal + gstAmount);
        double instantDiscount = Math.min(nonNull(bill.getInstantDiscountAmount()), totalBeforeInstantDiscount);

        bill.setSubTotalAmount(subTotal);
        bill.setGstAmount(gstAmount);
        bill.setGstApplied(gstAmount > 0.0001);
        bill.setInstantDiscountAmount(round2(instantDiscount));
        bill.setTotalAmount(round2(totalBeforeInstantDiscount - instantDiscount));
    }

    @Transactional(readOnly = true)
    public Bill lookupInvoice(String number) {
        Long id = parseBillId(number);
        if (id == null) throw new IllegalArgumentException("Invoice number is required.");
        return getBillByIdWithDetails(id);
    }

    private static double remainingTaxValue(Double amount, BillItem item) {
        if (item.getQuantity() == null || item.getQuantity() <= 0) return 0;
        java.math.BigDecimal original = GstCalculator.money(java.math.BigDecimal.valueOf(nonNull(amount)));
        java.math.BigDecimal reversed = original.multiply(java.math.BigDecimal.valueOf(nonNull(item.getReturnedQuantity())))
                .divide(java.math.BigDecimal.valueOf(item.getQuantity()), 2, java.math.RoundingMode.HALF_UP);
        return original.subtract(reversed).doubleValue();
    }

    private void adjustPaymentsToTotal(Bill bill, double newTotal, String referencePrefix) {
        ensurePayments(bill);

        double storedPaid = nonNull(bill.getPaidAmount());
        double paymentPaid = sumPaymentsByCreditFlag(bill, false);
        double paymentDue = sumPaymentsByCreditFlag(bill, true);
        double effectivePaid = Math.abs(paymentPaid) > 0.0001 ? paymentPaid : storedPaid;

        double desiredDue = round2(Math.max(0.0, newTotal - effectivePaid));
        double creditAdjustmentAmount = round2(desiredDue - paymentDue);
        if (Math.abs(creditAdjustmentAmount) > 0.0001) {
            BillPayment creditAdjustment = payment(bill, PaymentMethod.CREDIT, creditAdjustmentAmount, referencePrefix + " credit adjustment");
            bill.getPayments().add(creditAdjustment);
        }

        double excessTotal = round2(effectivePaid - newTotal);
        if (excessTotal > 0.0001) {
            // Earlier refunds already reduce the amount refundable through each payment mode.
            Map<PaymentMethod, Double> availableByMethod = new java.util.EnumMap<>(PaymentMethod.class);
            bill.getPayments().stream().filter(p -> p.getMethod() != null && p.getMethod() != PaymentMethod.CREDIT)
                    .forEach(p -> availableByMethod.merge(p.getMethod(), nonNull(p.getAmount()), Double::sum));
            for (BillPayment original : bill.getPayments().stream()
                    .filter(p -> p.getMethod() != null && p.getMethod() != PaymentMethod.CREDIT)
                    .filter(p -> nonNull(p.getAmount()) > 0.0001)
                    .sorted(Comparator.comparing(BillPayment::getId, Comparator.nullsLast(Long::compareTo)).reversed())
                    .toList()) {
                double available = Math.max(0, round2(availableByMethod.getOrDefault(original.getMethod(), 0.0)));
                double amount = round2(Math.min(Math.min(nonNull(original.getAmount()), available), excessTotal));
                if (amount <= 0.0001) continue;
                BillPayment refund = payment(bill, original.getMethod(), -amount, referencePrefix + " refund adjustment");
                bill.getPayments().add(refund);
                availableByMethod.put(original.getMethod(), round2(available - amount));
                excessTotal = round2(excessTotal - amount);
                if (excessTotal <= 0.0001) break;
            }
            if (excessTotal > 0.0001) throw new IllegalArgumentException("Refund cannot be allocated to recorded payments. Review this bill's payment history.");
        }

        recomputePaidAndDue(bill);
    }

    private double sumPaymentsByCreditFlag(Bill bill, boolean credit) {
        if (bill.getPayments() == null) {
            return 0.0;
        }
        return round2(bill.getPayments().stream()
                .filter(p -> p != null && p.getMethod() != null)
                .filter(p -> credit == (p.getMethod() == PaymentMethod.CREDIT))
                .mapToDouble(p -> nonNull(p.getAmount()))
                .sum());
    }

    private BillPayment payment(Bill bill, PaymentMethod method, double amount, String reference) {
        BillPayment payment = new BillPayment();
        payment.setBill(bill);
        payment.setMethod(method);
        payment.setAmount(round2(amount));
        payment.setReference(reference);
        return payment;
    }

    private void ensurePayments(Bill bill) {
        if (bill.getPayments() == null) {
            bill.setPayments(new LinkedHashSet<>());
        }
    }

    public BillStatus getEffectiveStatus(Bill bill) {
        return bill != null && bill.getStatus() != null ? bill.getStatus() : BillStatus.ACTIVE;
    }

    private boolean allItemsReturned(Bill bill) {
        return bill.getItems() != null && distinctBillItems(bill).stream().allMatch(item -> netQuantity(item) <= 0.0001);
    }

    private double netQuantity(BillItem item) {
        return round2(nonNull(item.getQuantity()) - nonNull(item.getReturnedQuantity()));
    }

    private List<BillItem> distinctBillItems(Bill bill) {
        if (bill == null || bill.getItems() == null) {
            return List.of();
        }
        Map<Long, BillItem> byId = new LinkedHashMap<>();
        List<BillItem> withoutId = new ArrayList<>();
        for (BillItem item : bill.getItems()) {
            if (item == null) continue;
            if (item.getId() == null) {
                withoutId.add(item);
            } else {
                byId.putIfAbsent(item.getId(), item);
            }
        }
        List<BillItem> items = new ArrayList<>(byId.values());
        items.addAll(withoutId);
        return items;
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

    private BillRegisterResponse.Item toBillRegisterItem(Bill bill) {
        BillRegisterResponse.Item item = new BillRegisterResponse.Item();
        item.setId(bill.getId());
        item.setBillNumber(bill.getTaxDocumentNumber() == null ? billNumber(bill.getId()) : bill.getTaxDocumentNumber());
        item.setCreatedAt(bill.getCreatedAt());
        item.setCustomerName(bill.getCustomerName());
        item.setContactInfo(bill.getContactInfo());
        item.setItemsCount(bill.getItems() != null ? bill.getItems().size() : 0);
        item.setTotalAmount(bill.getTotalAmount());
        item.setPaidAmount(bill.getPaidAmount());
        item.setDueAmount(bill.getDueAmount());

        if (bill.getSalesMan() != null) {
            item.setSalesmanEmployeeId(bill.getSalesMan().getEmployeeId());
            item.setSalesmanName(bill.getSalesMan().getName());
        }

        if (bill.getPayments() != null) {
            item.setPaymentMethods(bill.getPayments().stream()
                    .map(BillPayment::getMethod)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList());
        }
        return item;
    }

    private static String billNumber(Long id) {
        if (id == null) return null;
        return String.format("INV-%08d", id);
    }

    private Long parseBillId(String billNo) {
        String value = blankToNull(billNo);
        if (value != null && value.contains("/")) {
            return billRepository.findByTenantIdAndTaxDocumentNumber(StockService.requireTenant(), value)
                    .map(Bill::getId).orElse(-1L);
        }
        if (value == null) return null;
        String normalized = value.toUpperCase();
        if (normalized.startsWith("INV-")) {
            normalized = normalized.substring(4);
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static LocalDateTime normalizeFrom(LocalDate from) {
        return (from != null ? from : LocalDate.now()).atStartOfDay();
    }

    private static LocalDateTime normalizeTo(LocalDate to) {
        return (to != null ? to : LocalDate.now()).plusDays(1).atStartOfDay();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static double nonNull(Double amount) {
        return amount != null ? amount : 0.0;
    }

    private void applyPayments(Bill billRequest) {
        Double total = billRequest.getTotalAmount() != null ? billRequest.getTotalAmount() : 0.0;

        List<BillPayment> incoming = billRequest.getPayments() == null
                ? new ArrayList<>()
                : new ArrayList<>(billRequest.getPayments());

        double sum = 0.0;
        for (BillPayment p : incoming) {
            if (p == null) continue;
            if (p.getMethod() == null) throw new IllegalArgumentException("Payment method is required");
            if (p.getAmount() == null) throw new IllegalArgumentException("Payment amount is required");
            if (p.getAmount() < 0 && p.getMethod() != PaymentMethod.CREDIT) {
                throw new IllegalArgumentException("Payment amount must be >= 0");
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
            throw new IllegalArgumentException("Sum of payments exceeds bill total");
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

    private static double round2(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    private record ProductResolution(Product product, String barcodeUsed, double quantityPerScan) {
    }
}
