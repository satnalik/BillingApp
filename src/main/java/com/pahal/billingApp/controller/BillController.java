package com.pahal.billingApp.controller;

import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.repository.BillRepository;
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
    private BillRepository billRepository;

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
    public ResponseEntity<Bill> createBill(@RequestBody Bill bill) {
        Bill savedBill = billingService.createBill(bill);
        return ResponseEntity.ok(savedBill);
    }

    /**
     * 2. Get All Bills for the Current Tenant
     * Thanks to our AOP Filter, billRepository.findAll() will
     * only return bills belonging to the X-TenantID header.
     */
    @GetMapping
    public List<Bill> getAllBills() {
        return billRepository.findAll();
    }

    /**
     * 3. Get a Specific Bill by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Bill> getBillById(@PathVariable Long id) {
        return billRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 4. Generate and Download PDF for a Bill
     */
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<InputStreamResource> downloadBillPdf(@PathVariable Long id) {
        // Fetch the bill first to ensure it belongs to the tenant
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill not found or access denied"));

        ByteArrayInputStream bis = pdfService.generateBillPdf(bill);

        HttpHeaders headers = new HttpHeaders();
        // 'inline' opens it in the browser, 'attachment' forces a download
        headers.add("Content-Disposition", "inline; filename=invoice_" + id + ".pdf");

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(bis));
    }
}