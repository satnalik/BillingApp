package com.pahal.billingApp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pahal.billingApp.dto.*;
import com.pahal.billingApp.entity.*;
import com.pahal.billingApp.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.pahal.billingApp.service.GstCalculator.*;

@Service
public class GstService {
    private final GstSettingsRepository settings;
    private final GstDocumentRepository documents;
    private final GstPeriodRepository periods;
    private final GstAuditRepository audit;
    private final ProductRepository products;
    private final ProductBarcodeRepository barcodes;
    private final SupplierRepository suppliers;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final StockService stock;
    public GstService(GstSettingsRepository settings, GstDocumentRepository documents, GstPeriodRepository periods,
            GstAuditRepository audit, ProductRepository products, ProductBarcodeRepository barcodes, SupplierRepository suppliers, JdbcTemplate jdbc, ObjectMapper json, StockService stock) {
        this.settings = settings; this.documents = documents; this.periods = periods;
        this.audit = audit; this.products = products; this.barcodes = barcodes; this.suppliers = suppliers; this.jdbc = jdbc; this.json = json;
        this.stock = stock;
    }

    @Transactional(readOnly = true)
    public GstSettings settings() {
        return settings.findById(StockService.requireTenant()).orElseGet(() -> {
            GstSettings value = new GstSettings(); value.setTenantId(StockService.requireTenant()); return value;
        });
    }

    @Transactional
    public GstSettings saveSettings(GstSettings request) {
        GstSettings existing = lockedSettings();
        if (request == null) throw new IllegalArgumentException("GST settings are required.");
        if (!Set.of("REGULAR", "UNREGISTERED").contains(text(request.getRegistrationMode()))) throw new IllegalArgumentException("Choose Regular GST or Unregistered. Composition is not supported in this release.");
        if (existing.getVersion() != null && !Objects.equals(existing.getVersion(), request.getVersion())) throw new IllegalArgumentException("GST settings changed. Refresh before saving.");
        String gstin = gstin(request.getGstin());
        String state = state(request.getStateCode());
        String name = required(request.getLegalName(), 160, "Legal business name");
        String address = required(request.getAddress(), 1000, "Business address");
        if ("REGULAR".equals(request.getRegistrationMode()) && (gstin == null || !gstin.startsWith(state))) throw new IllegalArgumentException("A valid GSTIN matching the store state is required.");
        if (request.getEffectiveFrom() == null || request.getEffectiveFrom().isAfter(LocalDate.now())) throw new IllegalArgumentException("Choose an effective date on or before today.");
        if (!Set.of("INCLUSIVE", "EXCLUSIVE").contains(text(request.getPriceMode()))) throw new IllegalArgumentException("Choose inclusive or exclusive prices.");
        String prefix = required(request.getInvoicePrefix(), 4, "Invoice prefix").toUpperCase(Locale.ROOT);
        if (!prefix.matches("[A-Z]{1,4}")) throw new IllegalArgumentException("Invoice prefix must contain 1 to 4 letters.");
        if (documents.existsByTenantIdAndRegistrationMode(existing.getTenantId(), "REGULAR") && "REGULAR".equals(existing.getRegistrationMode()) &&
                (!Objects.equals(existing.getGstin(), gstin) || !Objects.equals(existing.getStateCode(), state)
                || !Objects.equals(existing.getRegistrationMode(), request.getRegistrationMode())
                || !Objects.equals(existing.getEffectiveFrom(), request.getEffectiveFrom())
                || !Objects.equals(existing.getInvoicePrefix(), prefix))) {
            throw new IllegalArgumentException("GST identity, effective date and numbering cannot change after posting. Use a separate tenant for a different registration.");
        }
        existing.setRegistrationMode(request.getRegistrationMode()); existing.setGstin("REGULAR".equals(request.getRegistrationMode()) ? gstin : null);
        existing.setStateCode(state); existing.setLegalName(name); existing.setAddress(address);
        existing.setPriceMode(request.getPriceMode()); existing.setEffectiveFrom(request.getEffectiveFrom()); existing.setInvoicePrefix(prefix);
        log("SETTINGS", "GST settings", name + " | " + existing.getRegistrationMode() + " | " + existing.getPriceMode());
        return settings.saveAndFlush(existing);
    }

    @Transactional
    public Product saveProductTax(Long id, GstDTO.ProductTax request) {
        if (request == null) throw new IllegalArgumentException("Product tax details are required.");
        Product product = stock.lockProduct(id);
        rate(request.taxCategory(), request.gstRate(), request.hsnCode(), request.unitCode());
        product.setHsnCode(request.hsnCode()); product.setUnitCode(request.unitCode());
        product.setTaxCategory(request.taxCategory()); product.setGstRate(request.gstRate());
        log("PRODUCT_TAX", id.toString(), request.toString());
        return products.save(product);
    }

    @Transactional(readOnly = true)
    public GstDTO.Quote quote(CreateBillRequest request) {
        GstSettings config = settings();
        if ("NOT_CONFIGURED".equals(config.getRegistrationMode())) return new GstDTO.Quote(false, "NOT_CONFIGURED", "EXCLUSIVE", null, null, null, null, null, null, null);
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) return new GstDTO.Quote(true, config.getRegistrationMode(), config.getPriceMode(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        List<Input> inputs = new ArrayList<>();
        for (CreateBillItemRequest item : request.getItems()) {
            if (item == null) throw new IllegalArgumentException("Sales quote item is required.");
            Product product = products.findByIdAndTenantId(item.getProductId(), config.getTenantId()).orElseThrow(() -> new IllegalArgumentException("Product not found."));
            double multiplier = item.getBarcode() == null ? 1 : barcodes.findByBarcodeAndTenantId(item.getBarcode().trim(), config.getTenantId())
                    .map(link -> {
                        if (!link.getProduct().getId().equals(product.getId())) throw new IllegalArgumentException("Barcode does not belong to the product.");
                        return link.getQuantityPerScan() == null ? 1d : link.getQuantityPerScan();
                    }).orElse(1d);
            inputs.add(new Input(null, product.getId(), product.getName(), item.getBarcode(), StockService.round2(value(item.getQuantity()) * multiplier),
                    item.getUnitSellingPrice() == null ? value(product.getSellingPrice() != null ? product.getSellingPrice() : product.getPrice()) : item.getUnitSellingPrice(), value(item.getDiscount()),
                    product.getHsnCode(), product.getUnitCode(), product.getTaxCategory(), product.getGstRate()));
        }
        Result result = calculate(inputs, value(request.getInstantDiscountAmount()), config.getPriceMode(), "REGULAR".equals(config.getRegistrationMode()),
                !config.getStateCode().equals(place(request.getPlaceOfSupply(), config.getStateCode())));
        return new GstDTO.Quote(true, config.getRegistrationMode(), config.getPriceMode(), result.taxable(), result.tax(), result.total(), result.beforeDiscount(), result.cgst(), result.sgst(), result.igst());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void prepareSale(Bill bill, CreateBillRequest request) {
        GstSettings config = lockedSettings();
        if ("NOT_CONFIGURED".equals(config.getRegistrationMode())) return;
        checkDate(config, LocalDate.now());
        bill.setCustomerGstin(gstin(request.getCustomerGstin()));
        bill.setCustomerAddress(optional(request.getCustomerAddress(), 1000));
        bill.setDeliveryAddress(optional(request.getDeliveryAddress(), 1000));
        if (bill.getCustomerGstin() != null && bill.getCustomerAddress() == null) throw new IllegalArgumentException("Business customer address is required with GSTIN.");
        if (bill.getCustomerGstin() != null && (text(bill.getCustomerName()).isEmpty() || "Guest".equalsIgnoreCase(text(bill.getCustomerName())))) throw new IllegalArgumentException("Enter the business customer's legal name with GSTIN.");
        bill.setPlaceOfSupply(place(request.getPlaceOfSupply(), config.getStateCode()));
        bill.setTaxRegistrationMode(config.getRegistrationMode()); bill.setTaxPriceMode(config.getPriceMode());
        List<Input> inputs = bill.getItems().stream().map(item -> {
            Product product = products.findByIdAndTenantId(item.getProductId(), config.getTenantId()).orElseThrow();
            return new Input(item.getId(), item.getProductId(), item.getProductName(), item.getBarcode(), value(item.getQuantity()),
                    value(item.getUnitSellingPrice()), value(item.getDiscount()), product.getHsnCode(), product.getUnitCode(), product.getTaxCategory(), product.getGstRate());
        }).toList();
        Result result = calculate(inputs, value(request.getInstantDiscountAmount()), config.getPriceMode(), "REGULAR".equals(config.getRegistrationMode()), !config.getStateCode().equals(bill.getPlaceOfSupply()));
        if ("REGULAR".equals(config.getRegistrationMode()) && result.total().compareTo(new BigDecimal("50000")) >= 0 && bill.getCustomerGstin() == null
                && (bill.getCustomerAddress() == null || text(bill.getCustomerName()).isEmpty() || "Guest".equalsIgnoreCase(text(bill.getCustomerName()))))
            throw new IllegalArgumentException("Enter the customer's name and address for an unregistered-customer bill of INR 50,000 or more. Include delivery address if different.");
        for (int index = 0; index < result.lines().size(); index++) {
            BillItem item = bill.getItems().get(index); GstDocument.Line line = result.lines().get(index);
            item.setHsnCode(line.getHsnCode()); item.setUnitCode(line.getUnitCode()); item.setTaxCategory(line.getTaxCategory());
            item.setGstRate(line.getGstRate().doubleValue()); item.setTaxableAmount(line.getTaxableAmount().doubleValue());
            item.setCgstAmount(line.getCgstAmount().doubleValue()); item.setSgstAmount(line.getSgstAmount().doubleValue()); item.setIgstAmount(line.getIgstAmount().doubleValue());
            item.setGstAmount(tax(line).doubleValue()); item.setFinalDiscountAmount(line.getDiscountAmount().doubleValue());
        }
        bill.setSubTotalAmount(result.taxable().doubleValue()); bill.setGstAmount(result.tax().doubleValue());
        bill.setGstApplied(result.tax().signum() > 0); bill.setTotalAmount(result.total().doubleValue());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void postSale(Bill bill) {
        if (bill.getTaxRegistrationMode() == null) return;
        GstSettings config = lockedSettings();
        GstDocument doc = base(config, "REGULAR".equals(bill.getTaxRegistrationMode()) ? "SALE" : "RECEIPT", "sale:" + bill.getId(), bill.getCreatedAt().toLocalDate());
        doc.setSourceId(bill.getId()); doc.setPartyName(optional(bill.getCustomerName(), 160)); doc.setPartyGstin(bill.getCustomerGstin());
        doc.setPartyAddress(bill.getCustomerAddress()); doc.setPlaceOfSupply(bill.getPlaceOfSupply());
        doc.setDeliveryAddress(bill.getDeliveryAddress() == null ? bill.getCustomerAddress() : bill.getDeliveryAddress());
        doc.setDocumentNumber(number(config, doc.getDocumentType(), doc.getDocumentDate()));
        for (BillItem item : bill.getItems()) {
            GstDocument.Line line = new GstDocument.Line();
            line.setSourceItemId(item.getId()); line.setProductId(item.getProductId()); line.setProductName(item.getProductName()); line.setBarcode(item.getBarcode());
            line.setHsnCode(item.getHsnCode()); line.setUnitCode(item.getUnitCode()); line.setTaxCategory(item.getTaxCategory()); line.setGstRate(decimal(value(item.getGstRate())));
            line.setQuantity(money(decimal(value(item.getQuantity())))); line.setTaxableAmount(money(decimal(value(item.getTaxableAmount()))));
            line.setCgstAmount(money(decimal(value(item.getCgstAmount())))); line.setSgstAmount(money(decimal(value(item.getSgstAmount())))); line.setIgstAmount(money(decimal(value(item.getIgstAmount()))));
            line.setDiscountAmount(money(decimal(value(item.getFinalDiscountAmount())))); doc.getLines().add(line);
        }
        total(doc); documents.save(doc); bill.setTaxDocumentNumber(doc.getDocumentNumber());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void preparePurchase(PurchaseBill bill, CreatePurchaseBillRequest request) {
        GstSettings config = lockedSettings();
        preparePurchaseWithConfig(bill, request, config);
    }

    private void preparePurchaseWithConfig(PurchaseBill bill, CreatePurchaseBillRequest request, GstSettings config) {
        if (!"REGULAR".equals(config.getRegistrationMode())) return;
        checkDate(config, bill.getBillDate());
        String supplierGstin = gstin(bill.getSupplier().getGstNumber());
        String supplierState = supplierGstin != null ? supplierGstin.substring(0, 2) : place(request.getSupplierStateCode(), config.getStateCode());
        String place = place(request.getPlaceOfSupply(), config.getStateCode());
        String pricing = text(request.getPriceMode()).isEmpty() ? "EXCLUSIVE" : request.getPriceMode();
        List<Input> inputs = new ArrayList<>();
        for (int index = 0; index < bill.getItems().size(); index++) {
            PurchaseBillItem item = bill.getItems().get(index); Product product = item.getProduct(); CreatePurchaseBillItemRequest source = request.getItems().get(index);
            String category = supplierGstin == null ? "NON_GST" : fallback(source.getTaxCategory(), product.getTaxCategory());
            Double rate = supplierGstin == null ? 0d : source.getGstRate() != null ? source.getGstRate() : product.getGstRate();
            inputs.add(new Input(item.getId(), product.getId(), item.getProductName(), item.getBarcode(), value(item.getQuantity()), value(item.getPurchasePrice()), 0,
                    fallback(source.getHsnCode(), product.getHsnCode()), fallback(source.getUnitCode(), product.getUnitCode()), category, rate));
        }
        Result result = calculate(inputs, value(request.getDiscountAmount()), pricing, true, !supplierState.equals(place));
        if (request.getTaxAmount() != null && request.getTaxAmount() != 0 && money(decimal(request.getTaxAmount())).compareTo(result.tax()) != 0)
            throw new IllegalArgumentException("Header tax differs from line GST. Leave header tax blank for calculated GST.");
        for (int index = 0; index < result.lines().size(); index++) {
            PurchaseBillItem item = bill.getItems().get(index); GstDocument.Line line = result.lines().get(index);
            item.setHsnCode(line.getHsnCode()); item.setUnitCode(line.getUnitCode()); item.setTaxCategory(line.getTaxCategory()); item.setGstRate(line.getGstRate().doubleValue());
            item.setTaxableAmount(line.getTaxableAmount().doubleValue()); item.setGstAmount(tax(line).doubleValue());
            item.setCgstAmount(line.getCgstAmount().doubleValue()); item.setSgstAmount(line.getSgstAmount().doubleValue()); item.setIgstAmount(line.getIgstAmount().doubleValue());
            // Return credit now follows this line's actual discounted gross amount.
            item.setLineTotal(line.getTaxableAmount().add(tax(line)).doubleValue());
        }
        bill.setSubTotalAmount(result.taxable().doubleValue()); bill.setTaxAmount(result.tax().doubleValue()); bill.setTotalAmount(result.total().doubleValue());
    }

    @Transactional(readOnly = true)
    public GstDTO.Quote purchaseQuote(CreatePurchaseBillRequest request) {
        GstSettings config = settings();
        if (!"REGULAR".equals(config.getRegistrationMode())) return new GstDTO.Quote(false, config.getRegistrationMode(), "EXCLUSIVE", null, null, null, null, null, null, null);
        if (request == null || request.getSupplierId() == null || request.getItems() == null || request.getItems().isEmpty()) throw new IllegalArgumentException("Choose supplier and purchase items.");
        PurchaseBill bill = new PurchaseBill(); bill.setBillDate(request.getBillDate() == null ? LocalDate.now() : request.getBillDate());
        bill.setSupplier(suppliers.findByIdAndTenantId(request.getSupplierId(), config.getTenantId()).orElseThrow(() -> new IllegalArgumentException("Supplier not found.")));
        List<PurchaseBillItem> items = new ArrayList<>();
        for (CreatePurchaseBillItemRequest source : request.getItems()) {
            if (source == null) throw new IllegalArgumentException("Purchase quote item is required.");
            Product product = products.findByIdAndTenantId(source.getProductId(), config.getTenantId()).orElseThrow(() -> new IllegalArgumentException("Product not found."));
            double multiplier = source.getQuantityPerScan() == null ? 1 : source.getQuantityPerScan();
            if (source.getBarcode() != null) multiplier = barcodes.findByBarcodeAndTenantId(source.getBarcode().trim(), config.getTenantId()).map(link -> {
                if (!link.getProduct().getId().equals(product.getId())) throw new IllegalArgumentException("Barcode does not belong to product.");
                return link.getQuantityPerScan() == null ? 1d : link.getQuantityPerScan();
            }).orElse(multiplier);
            PurchaseBillItem item = new PurchaseBillItem(); item.setProduct(product); item.setProductName(product.getName()); item.setBarcode(source.getBarcode());
            item.setQuantity(StockService.round2(value(source.getQuantity()) * multiplier)); item.setPurchasePrice(source.getPurchasePrice()); items.add(item);
        }
        bill.setItems(items); preparePurchaseWithConfig(bill, request, config);
        BigDecimal cgst = BigDecimal.ZERO, sgst = cgst, igst = cgst;
        for (PurchaseBillItem item : items) { cgst = cgst.add(decimal(value(item.getCgstAmount()))); sgst = sgst.add(decimal(value(item.getSgstAmount()))); igst = igst.add(decimal(value(item.getIgstAmount()))); }
        BigDecimal total = money(decimal(value(bill.getTotalAmount())));
        return new GstDTO.Quote(true, config.getRegistrationMode(), request.getPriceMode(), money(decimal(value(bill.getSubTotalAmount()))), money(decimal(value(bill.getTaxAmount()))), total, total.add(money(decimal(value(request.getDiscountAmount())))), cgst, sgst, igst);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void postPurchase(PurchaseBill bill, CreatePurchaseBillRequest request) {
        if (bill.getItems().isEmpty() || bill.getItems().get(0).getTaxableAmount() == null) return;
        GstSettings config = lockedSettings();
        GstDocument doc = base(config, "PURCHASE", "purchase:" + bill.getId(), bill.getBillDate());
        doc.setDocumentNumber(required(bill.getBillNumber(), 16, "Supplier invoice number")); doc.setSourceId(bill.getId());
        doc.setPartyName(required(bill.getSupplier().getName(), 160, "Supplier name")); doc.setPartyGstin(gstin(bill.getSupplier().getGstNumber())); doc.setPartyAddress(optional(bill.getSupplier().getAddress(), 1000));
        int year = bill.getBillDate().getMonthValue() >= 4 ? bill.getBillDate().getYear() : bill.getBillDate().getYear() - 1;
        if (doc.getPartyGstin() != null && documents.existsByTenantIdAndDocumentTypeAndPartyGstinAndDocumentNumberAndDocumentDateBetween(
                doc.getTenantId(), "PURCHASE", doc.getPartyGstin(), doc.getDocumentNumber(), LocalDate.of(year, 4, 1), LocalDate.of(year + 1, 3, 31)))
            throw new IllegalArgumentException("This supplier GSTIN and invoice number are already recorded in this financial year. Open the existing purchase instead.");
        doc.setPlaceOfSupply(place(request.getPlaceOfSupply(), config.getStateCode())); doc.setPriceMode(text(request.getPriceMode()).isEmpty() ? "EXCLUSIVE" : request.getPriceMode());
        doc.setItcStatus(doc.getPartyGstin() == null ? "INELIGIBLE" : "PENDING");
        for (PurchaseBillItem item : bill.getItems()) {
            GstDocument.Line line = new GstDocument.Line(); line.setSourceItemId(item.getId()); line.setProductId(item.getProduct().getId());
            line.setProductName(item.getProductName()); line.setBarcode(item.getBarcode()); line.setHsnCode(item.getHsnCode()); line.setUnitCode(item.getUnitCode()); line.setTaxCategory(item.getTaxCategory());
            line.setQuantity(money(decimal(value(item.getQuantity())))); line.setGstRate(decimal(value(item.getGstRate()))); line.setTaxableAmount(money(decimal(value(item.getTaxableAmount()))));
            line.setCgstAmount(money(decimal(value(item.getCgstAmount())))); line.setSgstAmount(money(decimal(value(item.getSgstAmount())))); line.setIgstAmount(money(decimal(value(item.getIgstAmount())))); line.setDiscountAmount(BigDecimal.ZERO);
            doc.getLines().add(line);
        }
        total(doc); documents.save(doc);
    }

    /** Cumulative rounding makes a complete return reverse the original tax exactly. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saleReturn(Bill bill, Map<Long, Double> before, String reason) {
        Optional<GstDocument> original = documents.findByTenantIdAndSourceKey(StockService.requireTenant(), "sale:" + bill.getId());
        if (original.isEmpty()) return;
        GstSettings config = lockedSettings();
        GstDocument doc = adjustmentBase(config, original.get(), "SALE_CREDIT", "sale-return:" + UUID.randomUUID(), LocalDate.now(), reason);
        for (BillItem item : bill.getItems()) {
            original.get().getLines().stream().filter(line -> Objects.equals(line.getSourceItemId(), item.getId())).findFirst().ifPresent(line -> {
                BigDecimal old = decimal(before.getOrDefault(item.getId(), 0d)), next = decimal(value(item.getReturnedQuantity()));
                if (next.compareTo(old) > 0) doc.getLines().add(returnLine(line, old, next));
            });
        }
        if (!doc.getLines().isEmpty()) { total(doc); documents.save(doc); }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void purchaseReturn(PurchaseBill bill, PurchaseReturn returned) {
        Optional<GstDocument> original = documents.findByTenantIdAndSourceKey(StockService.requireTenant(), "purchase:" + bill.getId());
        if (original.isEmpty()) return;
        GstSettings config = lockedSettings();
        GstDocument doc = adjustmentBase(config, original.get(), "PURCHASE_CREDIT", "purchase-return:" + returned.getId(), LocalDate.now(), returned.getReason());
        doc.setItcStatus(original.get().getPartyGstin() == null ? "INELIGIBLE" : "PENDING");
        for (PurchaseReturn.Item item : returned.getItems()) {
            PurchaseBillItem current = bill.getItems().stream().filter(i -> Objects.equals(i.getId(), item.getPurchaseItemId())).findFirst().orElseThrow();
            GstDocument.Line line = original.get().getLines().stream().filter(i -> Objects.equals(i.getSourceItemId(), item.getPurchaseItemId())).findFirst().orElseThrow();
            doc.getLines().add(returnLine(line, decimal(value(current.getReturnedQuantity())).subtract(decimal(item.getQuantity())), decimal(value(current.getReturnedQuantity()))));
        }
        total(doc); documents.save(doc);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void purchaseCancelled(PurchaseBill bill) {
        Optional<GstDocument> original = documents.findByTenantIdAndSourceKey(StockService.requireTenant(), "purchase:" + bill.getId());
        if (original.isEmpty()) return;
        GstSettings config = lockedSettings();
        GstDocument doc = adjustmentBase(config, original.get(), "PURCHASE_CANCEL", "purchase-cancel:" + bill.getId(), LocalDate.now(), bill.getCancelReason());
        doc.setItcStatus(original.get().getItcStatus());
        for (GstDocument.Line line : original.get().getLines()) doc.getLines().add(returnLine(line, BigDecimal.ZERO, line.getQuantity()));
        total(doc); documents.save(doc);
    }

    @Transactional
    public GstDocument review(Long id, GstDTO.Review request) {
        if (request == null) throw new IllegalArgumentException("Input tax review is required.");
        lockedSettings();
        GstDocument doc = document(id); requireOpen(doc.getDocumentDate()); requireOpen(doc.getReportingDate());
        if ("PURCHASE_CANCEL".equals(doc.getDocumentType())) throw new IllegalArgumentException("Review the original purchase; its cancellation follows the same review status.");
        if (!doc.getDocumentType().startsWith("PURCHASE") || !Set.of("PENDING", "ELIGIBLE", "INELIGIBLE").contains(text(request.status()))) throw new IllegalArgumentException("Choose a valid purchase ITC review status.");
        if (doc.getPartyGstin() == null && "ELIGIBLE".equals(request.status())) throw new IllegalArgumentException("An unregistered supplier invoice cannot be marked eligible.");
        if ("PURCHASE_CREDIT".equals(doc.getDocumentType()) && "ELIGIBLE".equals(request.status()) && (text(request.supplierNoteNumber()).isEmpty() || request.supplierNoteDate() == null))
            throw new IllegalArgumentException("Record the supplier credit-note number and date before reviewing this tax reversal.");
        if (request.supplierNoteDate() != null && (request.supplierNoteDate().isAfter(LocalDate.now()) || request.supplierNoteDate().isBefore(doc.getOriginalDocumentId() == null ? doc.getDocumentDate() : document(doc.getOriginalDocumentId()).getDocumentDate())))
            throw new IllegalArgumentException("Supplier note date must be between the original invoice date and today.");
        if (request.supplierNoteDate() != null) {
            requireOpen(request.supplierNoteDate());
            if (doc.getSourceKey().startsWith("manual:") && !request.supplierNoteDate().equals(doc.getDocumentDate())) throw new IllegalArgumentException("A manually recorded supplier note's date cannot be changed through input review.");
        }
        doc.setReviewNotes(required(request.notes(), 1000, "Review notes")); doc.setItcStatus(request.status());
        doc.setSupplierNoteNumber(optional(request.supplierNoteNumber(), 64)); doc.setSupplierNoteDate(request.supplierNoteDate());
        doc.setReviewedAt(LocalDateTime.now()); doc.setReviewedBy(actor());
        if ("PURCHASE".equals(doc.getDocumentType())) {
            for (GstDocument cancellation : documents.findByTenantIdAndOriginalDocumentId(doc.getTenantId(), doc.getId())) {
                if ("PURCHASE_CANCEL".equals(cancellation.getDocumentType())) {
                    requireOpen(cancellation.getDocumentDate());
                    cancellation.setItcStatus(doc.getItcStatus()); cancellation.setReviewNotes(doc.getReviewNotes());
                    cancellation.setReviewedAt(doc.getReviewedAt()); cancellation.setReviewedBy(doc.getReviewedBy());
                    documents.save(cancellation);
                }
            }
        }
        log("ITC_REVIEW", id.toString(), request.toString()); return documents.save(doc);
    }

    @Transactional
    public GstDocument manualAdjustment(GstDTO.Adjustment request) {
        if (request == null || request.requestKey() == null || request.originalDocumentId() == null) throw new IllegalArgumentException("Original invoice and adjustment request key are required.");
        GstSettings config = lockedSettings();
        if (!"REGULAR".equals(config.getRegistrationMode())) throw new IllegalArgumentException("Configure regular GST first.");
        String key = "manual:" + UUID.fromString(request.requestKey());
        String fingerprint;
        try { fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request))); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid adjustment details."); }
        Optional<GstDocument> previous = documents.findByTenantIdAndSourceKey(config.getTenantId(), key);
        if (previous.isPresent()) {
            if (!fingerprint.equals(previous.get().getRequestFingerprint())) throw new IllegalArgumentException("This adjustment request was already saved with different details.");
            return previous.get();
        }
        GstDocument original = document(request.originalDocumentId());
        String type = text(request.documentType());
        if (!Set.of("SALE_CREDIT", "SALE_DEBIT", "PURCHASE_CREDIT", "PURCHASE_DEBIT").contains(type)
                || !(original.getDocumentType().equals(type.startsWith("SALE") ? "SALE" : "PURCHASE"))) throw new IllegalArgumentException("Select an original sale/purchase invoice and matching note type.");
        if (request.documentDate() == null || request.documentDate().isBefore(original.getDocumentDate())) throw new IllegalArgumentException("Note date cannot precede the invoice.");
        GstDocument doc = adjustmentBase(config, original, type, key, request.documentDate(), required(request.reason(), 1000, "Note reason"));
        if (type.startsWith("PURCHASE")) {
            doc.setDocumentNumber(required(request.documentNumber(), 16, "Supplier note number")); doc.setSupplierNoteNumber(doc.getDocumentNumber()); doc.setSupplierNoteDate(request.documentDate()); doc.setItcStatus("PENDING");
        }
        if (request.lines() == null || request.lines().isEmpty() || request.lines().size() > 1000) throw new IllegalArgumentException("Add 1 to 1,000 adjustment lines.");
        for (GstDTO.AdjustmentLine line : request.lines()) {
            if (line == null) throw new IllegalArgumentException("Adjustment line is required.");
            double qty = value(line.quantity()), base = value(line.taxableAmount());
            if (qty < 0 || base <= 0) throw new IllegalArgumentException("Adjustment quantity cannot be negative and taxable value must be positive. Use zero quantity for a price adjustment.");
            if (type.startsWith("PURCHASE") && original.getPartyGstin() == null && value(line.gstRate()) > 0) throw new IllegalArgumentException("An unregistered supplier cannot charge GST.");
            Result calculated = calculate(List.of(new Input(null, line.productId(), required(line.productName(), 255, "Description"), null, 1, base, 0,
                    line.hsnCode(), line.unitCode(), line.taxCategory(), line.gstRate())), 0, "EXCLUSIVE", true,
                    type.startsWith("SALE") ? !original.getStoreState().equals(original.getPlaceOfSupply()) : original.getPartyGstin() != null && !original.getPartyGstin().substring(0, 2).equals(original.getPlaceOfSupply()));
            GstDocument.Line snapshot = calculated.lines().get(0); snapshot.setQuantity(money(decimal(qty)));
            if (type.endsWith("CREDIT")) negate(snapshot); doc.getLines().add(snapshot);
        }
        total(doc); doc.setRequestFingerprint(fingerprint); documents.save(doc);
        log("MANUAL_NOTE", doc.getDocumentNumber(), request.reason()); return doc;
    }

    @Transactional(readOnly = true) public GstDocument document(Long id) {
        return documents.findByIdAndTenantId(id, StockService.requireTenant()).orElseThrow(() -> new IllegalArgumentException("GST document not found."));
    }
    @Transactional(readOnly = true) public GstDocument saleDocument(Long billId) {
        return documents.findByTenantIdAndSourceKey(StockService.requireTenant(), "sale:" + billId).orElseThrow(() -> new IllegalArgumentException("This invoice has no GST snapshot. Legacy bills cannot be reconstructed automatically."));
    }
    @Transactional(readOnly = true) public List<GstPeriod> periods() { return periods.findByTenantIdOrderByPeriodMonthDesc(StockService.requireTenant()); }
    @Transactional(readOnly = true) public List<GstAudit> audit() { return audit.findTop200ByTenantIdOrderByIdDesc(StockService.requireTenant()); }
    @Transactional public GstPeriod changePeriod(GstDTO.PeriodChange request) {
        if (request == null || request.month() == null) throw new IllegalArgumentException("GST period is required.");
        GstSettings config = lockedSettings(); YearMonth month;
        try { month = YearMonth.parse(request.month()); }
        catch (java.time.DateTimeException exception) { throw new IllegalArgumentException("Use a valid GST month in YYYY-MM format."); }
        if ("NOT_CONFIGURED".equals(config.getRegistrationMode())) throw new IllegalArgumentException("Configure GST settings before reviewing periods.");
        if (request.locked() && !month.isBefore(YearMonth.now())) throw new IllegalArgumentException("Only completed months can be locked.");
        GstPeriod period = periods.findByTenantIdAndPeriodMonth(config.getTenantId(), month.toString()).orElseGet(GstPeriod::new);
        period.setTenantId(config.getTenantId()); period.setPeriodMonth(month.toString()); period.setLocked(request.locked());
        period.setNotes(required(request.notes(), 1000, "Period review/reopen reason")); period.setChangedAt(LocalDateTime.now()); period.setChangedBy(actor());
        log(request.locked() ? "PERIOD_LOCK" : "PERIOD_REOPEN", month.toString(), request.notes()); return periods.save(period);
    }

    private GstSettings lockedSettings() {
        String tenant = StockService.requireTenant();
        // Also serialize first setup, when no settings row exists yet. Shared JPA/JDBC transaction.
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, "gst:" + tenant); statement.execute();
            } return null;
        });
        return settings.lock(tenant).orElseGet(() -> { GstSettings value = new GstSettings(); value.setTenantId(tenant); return value; });
    }
    private GstDocument base(GstSettings config, String type, String key, LocalDate date) {
        checkDate(config, date); GstDocument doc = new GstDocument();
        doc.setTenantId(config.getTenantId()); doc.setDocumentType(type); doc.setSourceKey(key); doc.setDocumentDate(date);
        doc.setRegistrationMode(config.getRegistrationMode()); doc.setStoreGstin(config.getGstin()); doc.setStoreName(config.getLegalName()); doc.setStoreAddress(config.getAddress());
        doc.setStoreState(config.getStateCode()); doc.setPriceMode(config.getPriceMode()); doc.setRecordedAt(LocalDateTime.now()); doc.setActorUserId(actor()); return doc;
    }
    private GstDocument adjustmentBase(GstSettings config, GstDocument original, String type, String key, LocalDate date, String reason) {
        GstDocument doc = base(config, type, key, date); doc.setOriginalDocumentId(original.getId()); doc.setOriginalDocumentNumber(original.getDocumentNumber());
        doc.setRegistrationMode(original.getRegistrationMode()); doc.setPriceMode(original.getPriceMode());
        doc.setStoreGstin(original.getStoreGstin()); doc.setStoreState(original.getStoreState());
        doc.setSourceId(original.getSourceId()); doc.setPartyName(original.getPartyName()); doc.setPartyGstin(original.getPartyGstin()); doc.setPartyAddress(original.getPartyAddress());
        doc.setDeliveryAddress(original.getDeliveryAddress());
        doc.setPlaceOfSupply(original.getPlaceOfSupply()); doc.setReason(optional(reason, 1000)); doc.setDocumentNumber(number(config, type, date)); return doc;
    }
    private String number(GstSettings config, String type, LocalDate date) {
        int year = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        long serial = documents.countSeries(config.getTenantId(), type, LocalDate.of(year, 4, 1), LocalDate.of(year + 1, 3, 31)) + 1;
        if (serial > 9_999_999) throw new IllegalArgumentException("Document numbering exhausted for this financial year.");
        String prefix = config.getInvoicePrefix();
        if (!"SALE".equals(type)) prefix = (type.endsWith("CREDIT") ? "C" : type.endsWith("DEBIT") ? "D" : type.equals("RECEIPT") ? "R" : "X") + prefix.substring(0, Math.min(3, prefix.length()));
        if ("RECEIPT".equals(type) && prefix.equals(config.getInvoicePrefix())) prefix = prefix.substring(0, 3);
        return String.format("%s%02d%02d/%07d", prefix, year % 100, (year + 1) % 100, serial);
    }
    private void checkDate(GstSettings config, LocalDate date) {
        if (date == null || date.isAfter(LocalDate.now()) || config.getEffectiveFrom() == null || date.isBefore(config.getEffectiveFrom())) throw new IllegalArgumentException("Document date must be between GST setup's effective date and today.");
        requireOpen(date);
    }
    private void requireOpen(LocalDate date) {
        if (periods.findByTenantIdAndPeriodMonth(StockService.requireTenant(), YearMonth.from(date).toString()).map(GstPeriod::isLocked).orElse(false)) throw new IllegalArgumentException("This GST period is locked. An administrator must reopen it with a reason.");
    }
    private static GstDocument.Line returnLine(GstDocument.Line original, BigDecimal before, BigDecimal after) {
        GstDocument.Line line = new GstDocument.Line(); line.setSourceItemId(original.getSourceItemId()); line.setProductId(original.getProductId()); line.setProductName(original.getProductName()); line.setBarcode(original.getBarcode());
        line.setHsnCode(original.getHsnCode()); line.setUnitCode(original.getUnitCode()); line.setTaxCategory(original.getTaxCategory()); line.setGstRate(original.getGstRate());
        line.setQuantity(after.subtract(before).negate());
        line.setTaxableAmount(reverse(original.getTaxableAmount(), original.getQuantity(), before, after)); line.setCgstAmount(reverse(original.getCgstAmount(), original.getQuantity(), before, after));
        line.setSgstAmount(reverse(original.getSgstAmount(), original.getQuantity(), before, after)); line.setIgstAmount(reverse(original.getIgstAmount(), original.getQuantity(), before, after));
        line.setDiscountAmount(reverse(original.getDiscountAmount(), original.getQuantity(), before, after)); return line;
    }
    private static BigDecimal reverse(BigDecimal amount, BigDecimal qty, BigDecimal before, BigDecimal after) {
        return amount.multiply(before).divide(qty, 2, java.math.RoundingMode.HALF_UP).subtract(amount.multiply(after).divide(qty, 2, java.math.RoundingMode.HALF_UP));
    }
    private static void negate(GstDocument.Line line) {
        line.setQuantity(line.getQuantity().negate()); line.setTaxableAmount(line.getTaxableAmount().negate()); line.setCgstAmount(line.getCgstAmount().negate()); line.setSgstAmount(line.getSgstAmount().negate()); line.setIgstAmount(line.getIgstAmount().negate());
    }
    private static BigDecimal tax(GstDocument.Line line) { return line.getCgstAmount().add(line.getSgstAmount()).add(line.getIgstAmount()); }
    private static void total(GstDocument doc) {
        BigDecimal base = BigDecimal.ZERO, cgst = base, sgst = base, igst = base;
        for (GstDocument.Line line : doc.getLines()) { base = base.add(line.getTaxableAmount()); cgst = cgst.add(line.getCgstAmount()); sgst = sgst.add(line.getSgstAmount()); igst = igst.add(line.getIgstAmount()); }
        BigDecimal gross = base.add(cgst).add(sgst).add(igst);
        if (gross.abs().compareTo(new BigDecimal("1000000000")) > 0) throw new IllegalArgumentException("GST document exceeds the supported amount limit.");
        doc.setTaxableAmount(base); doc.setCgstAmount(cgst); doc.setSgstAmount(sgst); doc.setIgstAmount(igst); doc.setTotalAmount(gross);
    }
    private void log(String action, String reference, String details) {
        GstAudit entry = new GstAudit(); entry.setTenantId(StockService.requireTenant()); entry.setAction(action); entry.setReference(reference); entry.setDetails(details); entry.setRecordedAt(LocalDateTime.now()); entry.setActorUserId(actor()); audit.save(entry);
    }
    public static String gstin(String value) {
        String normalized = text(value).toUpperCase(Locale.ROOT); if (normalized.isEmpty()) return null;
        if (!normalized.matches("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]")) throw new IllegalArgumentException("GSTIN must have a valid 15-character format.");
        state(normalized.substring(0, 2));
        int factor = 2, sum = 0;
        for (int index = 13; index >= 0; index--) { int code = Character.digit(normalized.charAt(index), 36) * factor; sum += code / 36 + code % 36; factor = factor == 2 ? 1 : 2; }
        if (Character.digit(normalized.charAt(14), 36) != (36 - sum % 36) % 36) throw new IllegalArgumentException("GSTIN checksum is invalid.");
        return normalized;
    }
    public static String state(String value) {
        String normalized = text(value); if (!normalized.matches("[0-9]{2}") || Integer.parseInt(normalized) < 1 || Integer.parseInt(normalized) > 38) throw new IllegalArgumentException("Choose a valid Indian state/UT code (01-38)."); return normalized;
    }
    public static String stateName(String code) {
        String[] names = {"", "Jammu & Kashmir", "Himachal Pradesh", "Punjab", "Chandigarh", "Uttarakhand", "Haryana", "Delhi", "Rajasthan", "Uttar Pradesh", "Bihar", "Sikkim", "Arunachal Pradesh", "Nagaland", "Manipur", "Mizoram", "Tripura", "Meghalaya", "Assam", "West Bengal", "Jharkhand", "Odisha", "Chhattisgarh", "Madhya Pradesh", "Gujarat", "Daman & Diu (legacy)", "Dadra & Nagar Haveli and Daman & Diu", "Maharashtra", "Andhra Pradesh (legacy)", "Karnataka", "Goa", "Lakshadweep", "Kerala", "Tamil Nadu", "Puducherry", "Andaman & Nicobar Islands", "Telangana", "Andhra Pradesh", "Ladakh"};
        return code != null && code.matches("[0-9]{2}") && Integer.parseInt(code) > 0 && Integer.parseInt(code) < names.length ? names[Integer.parseInt(code)] + " (" + code + ")" : "-";
    }
    private static String place(String value, String fallback) { return text(value).isEmpty() ? fallback : state(value); }
    private static String fallback(String value, String fallback) { return text(value).isEmpty() ? fallback : value.trim(); }
    private static double value(Double value) { return value == null ? 0 : value; }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static String optional(String value, int max) { return text(value).isEmpty() ? null : required(value, max, "Text"); }
    private static String required(String value, int max, String label) { String normalized = text(value); if (normalized.isEmpty() || normalized.length() > max) throw new IllegalArgumentException(label + " is required and must be at most " + max + " characters."); return normalized; }
    private static String actor() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
}
