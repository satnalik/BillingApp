package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.AddBillPaymentRequest;
import com.pahal.billingApp.dto.BillResponse;
import com.pahal.billingApp.dto.CreateBillRequest;
import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.service.BillingService;
import com.pahal.billingApp.service.PdfGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.util.List;

@RestController
@RequestMapping("/api/bills")
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
    @GetMapping
    public List<BillResponse> getAllBills() {
        return billingService.getAllBillsWithDetails()
                .stream()
                .map(BillResponseMapper::toResponse)
                .toList();
    }

    /**
     * 3. Get a Specific Bill by ID
     */
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
     * Collect due against an existing bill (records payment + negative CREDIT adjustment).
     */
    @PostMapping("/{id}/payments")
    public ResponseEntity<BillResponse> addDuePayment(@PathVariable Long id, @RequestBody AddBillPaymentRequest request) {
        Bill updated = billingService.addDuePayment(id, request);
        return ResponseEntity.ok(BillResponseMapper.toResponse(updated));
    }

    /**
     * 4. Generate and Download PDF for a Bill
     */
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
        if (id == null) return null;
        return String.format("INV-%08d", id);
    }

    static BillResponse toResponse(Bill bill) {
        BillResponse r = new BillResponse();
        r.setId(bill.getId());
        r.setBillNumber(billNumber(bill.getId()));
        r.setCustomerName(bill.getCustomerName());
        r.setContactInfo(bill.getContactInfo());
        r.setTotalAmount(bill.getTotalAmount());
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
                        it.setProductName(i.getProductName());
                        it.setQuantity(i.getQuantity());
                        it.setUnitSellingPrice(i.getUnitSellingPrice());
                        it.setDiscount(i.getDiscount());
                        return it;
                    }).toList()
            );
        }

        if (bill.getPayments() != null) {
            r.setPayments(
                    bill.getPayments().stream().map(p -> {
                        BillResponse.Payment pr = new BillResponse.Payment();
                        pr.setMethod(p.getMethod());
                        pr.setAmount(p.getAmount());
                        pr.setReference(p.getReference());
                        return pr;
                    }).toList()
            );
        }

        return r;
    }
}
