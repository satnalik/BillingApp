package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.AddBillPaymentRequest;
import com.pahal.billingApp.dto.BillRegisterResponse;
import com.pahal.billingApp.dto.BillRegisterSummaryResponse;
import com.pahal.billingApp.dto.BillResponse;
import com.pahal.billingApp.dto.CreateBillRequest;
import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.enums.PaymentMethod;
import com.pahal.billingApp.service.BillingService;
import com.pahal.billingApp.service.PdfGeneratorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bills")
@Tag(name = "Billing API", description = "Endpoints for creating bills, adding payments, and generating PDFs")
public class BillController {

    @Autowired
    private BillingService billingService;

    @Autowired
    private PdfGeneratorService pdfService;

    /**
     * 1. Create a New Bill
     * This calls the service which:
     * - Validates product existence
     * - Checks/Reduces stock in Azure
     * - Calculates totals
     * - Saves the bill with the tenant_id from the header
     */
    @Operation(summary = "Create a New Bill", description = "Creates a new bill with the provided details. Validates products, checks stock, and calculates totals.")
    @PostMapping
    public ResponseEntity<BillResponse> createBill(@RequestBody CreateBillRequest request) {
        Bill savedBill = billingService.createBill(request);
        return ResponseEntity.ok(BillResponseMapper.toResponse(savedBill));
    }

    /**
     * 2. Get All Bills for the Current Tenant
     * Thanks to our AOP Filter, billRepository.findAll() will
     * only return bills belonging to the X-TenantID header.
     */
    @Operation(summary = "Get All Bills", description = "Retrieves all bills for the current tenant.")
    @GetMapping
    public List<BillResponse> getAllBills() {
        return billingService.getAllBillsWithDetails()
                .stream()
                .map(BillResponseMapper::toResponse)
                .toList();
    }

    @Operation(summary = "Bill Register", description = "Returns a paged, filterable sales bill register.")
    @GetMapping("/register")
    public ResponseEntity<BillRegisterResponse> getBillRegister(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String salesmanId,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(defaultValue = "false") boolean dueOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(billingService.getBillRegister(
                from, to, billNo, customer, phone, salesmanId, paymentMethod, dueOnly, page, size));
    }

    @Operation(summary = "Bill Register Summary", description = "Returns totals for the same filters used by the bill register.")
    @GetMapping("/register/summary")
    public ResponseEntity<BillRegisterSummaryResponse> getBillRegisterSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String salesmanId,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(defaultValue = "false") boolean dueOnly) {
        return ResponseEntity.ok(billingService.getBillRegisterSummary(
                from, to, billNo, customer, phone, salesmanId, paymentMethod, dueOnly));
    }

    /**
     * 3. Get a Specific Bill by ID
     */
    @Operation(summary = "Get Bill by ID", description = "Retrieves a specific bill by its ID. The bill must belong to the current tenant.")
    @GetMapping("/{id}")
    public ResponseEntity<BillResponse> getBillById(@PathVariable Long id) {
        try {
            Bill bill = billingService.getBillByIdWithDetails(id);
            return ResponseEntity.ok(BillResponseMapper.toResponse(bill));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Collect due against an existing bill (records payment + negative CREDIT
     * adjustment).
     */
    @Operation(summary = "Add Due Payment", description = "Records a payment for an existing bill and adjusts the due amount.")
    @PostMapping("/{id}/payments")
    public ResponseEntity<BillResponse> addDuePayment(@PathVariable Long id,
            @RequestBody AddBillPaymentRequest request) {
        Bill updated = billingService.addDuePayment(id, request);
        return ResponseEntity.ok(BillResponseMapper.toResponse(updated));
    }

    /**
     * 4. Generate and Download PDF for a Bill
     */
    @Operation(summary = "Download Bill PDF", description = "Generates and downloads the PDF for a specific bill.")
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<InputStreamResource> downloadBillPdf(@PathVariable Long id) {
        // Fetch the bill first to ensure it belongs to the tenant
        Bill bill = billingService.getBillByIdWithDetails(id);

        ByteArrayInputStream bis = pdfService.generateBillPdf(bill);

        HttpHeaders headers = new HttpHeaders();
        // 'inline' opens it in the browser, 'attachment' forces a download
        headers.add("Content-Disposition", "inline; filename=" + BillResponseMapper.billNumber(bill.getId()) + ".pdf");

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(bis));
    }
}

class BillResponseMapper {
    static String billNumber(Long id) {
        if (id == null)
            return null;
        return String.format("INV-%08d", id);
    }

    static BillResponse toResponse(Bill bill) {
        BillResponse r = new BillResponse();
        r.setId(bill.getId());
        r.setBillNumber(billNumber(bill.getId()));
        r.setCustomerName(bill.getCustomerName());
        r.setContactInfo(bill.getContactInfo());
        r.setTotalAmount(bill.getTotalAmount());
        r.setSubTotalAmount(bill.getSubTotalAmount());
        r.setGstApplied(bill.getGstApplied());
        r.setGstRate(bill.getGstRate());
        r.setGstAmount(bill.getGstAmount());
        r.setInstantDiscountAmount(bill.getInstantDiscountAmount());
        r.setCreatedAt(bill.getCreatedAt());
        r.setTenantId(bill.getTenantId());
        r.setPaidAmount(bill.getPaidAmount());
        r.setDueAmount(bill.getDueAmount());

        if (bill.getSalesMan() != null) {
            r.setSalesmanEmployeeId(bill.getSalesMan().getEmployeeId());
            r.setSalesmanName(bill.getSalesMan().getName());
        }

        if (bill.getItems() != null) {
            r.setItems(
                    bill.getItems().stream().map(i -> {
                        BillResponse.Item it = new BillResponse.Item();
                        it.setProductId(i.getProductId());
                        it.setBarcode(i.getBarcode());
                        it.setProductName(i.getProductName());
                        it.setQuantity(i.getQuantity());
                        it.setUnitSellingPrice(i.getUnitSellingPrice());
                        it.setDiscount(i.getDiscount());
                        it.setHsnCode(i.getHsnCode());
                        it.setGstRate(i.getGstRate());
                        it.setTaxableAmount(i.getTaxableAmount());
                        it.setGstAmount(i.getGstAmount());
                        return it;
                    }).toList());
        }

        if (bill.getPayments() != null) {
            r.setPayments(
                    bill.getPayments().stream().map(p -> {
                        BillResponse.Payment pr = new BillResponse.Payment();
                        pr.setId(p.getId());
                        pr.setMethod(p.getMethod());
                        pr.setAmount(p.getAmount());
                        pr.setReference(p.getReference());
                        pr.setCreatedAt(p.getCreatedAt());
                        return pr;
                    }).toList());
        }

        return r;
    }
}
