package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.CancelPurchaseBillRequest;
import com.pahal.billingApp.dto.BarcodeLabelPdfRequest;
import com.pahal.billingApp.dto.AddPurchasePaymentRequest;
import com.pahal.billingApp.dto.CreatePurchaseBillRequest;
import com.pahal.billingApp.dto.PurchaseBarcodeLabelResponse;
import com.pahal.billingApp.dto.PurchaseBillResponse;
import com.pahal.billingApp.entity.PurchaseBill;
import com.pahal.billingApp.entity.PurchaseBillItem;
import com.pahal.billingApp.entity.PurchasePayment;
import com.pahal.billingApp.enums.PurchaseStatus;
import com.pahal.billingApp.service.BarcodeLabelPdfService;
import com.pahal.billingApp.service.PurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/purchases")
@Tag(name = "Purchase API", description = "Endpoints for supplier purchase bills and stock inward")
public class PurchaseController {

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private BarcodeLabelPdfService barcodeLabelPdfService;

    @Operation(summary = "Create Purchase Bill", description = "Creates a supplier purchase bill and increases product stock.")
    @PostMapping
    public ResponseEntity<PurchaseBillResponse> createPurchaseBill(@RequestBody CreatePurchaseBillRequest request) {
        PurchaseBill saved = purchaseService.createPurchaseBill(request);
        return ResponseEntity.ok(PurchaseBillResponseMapper.toResponse(saved));
    }

    @Operation(summary = "Get All Purchase Bills", description = "Retrieves purchase bills for the current tenant.")
    @GetMapping
    public List<PurchaseBillResponse> getAllPurchaseBills() {
        return purchaseService.getAllPurchaseBills()
                .stream()
                .map(PurchaseBillResponseMapper::toResponse)
                .toList();
    }

    @Operation(summary = "Get Purchase Bill", description = "Retrieves one purchase bill by id.")
    @GetMapping("/{id}")
    public ResponseEntity<PurchaseBillResponse> getPurchaseBill(@PathVariable Long id) {
        return ResponseEntity.ok(PurchaseBillResponseMapper.toResponse(purchaseService.getPurchaseBill(id)));
    }

    @Operation(summary = "Cancel Purchase Bill", description = "Cancels a purchase bill and reverses product stock.")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<PurchaseBillResponse> cancelPurchaseBill(
            @PathVariable Long id,
            @RequestBody(required = false) CancelPurchaseBillRequest request) {
        return ResponseEntity.ok(PurchaseBillResponseMapper.toResponse(purchaseService.cancelPurchaseBill(id, request)));
    }

    @Operation(summary = "Add Supplier Due Payment", description = "Records a payment against a purchase bill and reduces supplier due.")
    @PostMapping("/{id}/payments")
    public ResponseEntity<PurchaseBillResponse> addDuePayment(
            @PathVariable Long id,
            @RequestBody AddPurchasePaymentRequest request) {
        return ResponseEntity.ok(PurchaseBillResponseMapper.toResponse(purchaseService.addDuePayment(id, request)));
    }

    @Operation(summary = "Generate Purchase Barcode Labels", description = "Generates missing product barcodes and returns label rows for a purchase bill.")
    @PostMapping("/{id}/barcode-labels/generate")
    public ResponseEntity<PurchaseBarcodeLabelResponse> generateBarcodeLabels(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseService.generateBarcodeLabels(id));
    }

    @Operation(summary = "Download Purchase Barcode Label PDF", description = "Generates a printable A4 PDF with Code 128 barcode labels for a purchase bill.")
    @PostMapping(value = "/{id}/barcode-labels/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<InputStreamResource> downloadBarcodeLabelsPdf(
            @PathVariable Long id,
            @RequestBody(required = false) BarcodeLabelPdfRequest request) {
        PurchaseBarcodeLabelResponse labels = purchaseService.generateBarcodeLabels(id);
        String fileName = "barcode-labels-purchase-" + id + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "inline; filename=" + fileName);

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(barcodeLabelPdfService.generatePurchaseLabelsPdf(labels, request)));
    }
}

class PurchaseBillResponseMapper {
    static PurchaseBillResponse toResponse(PurchaseBill bill) {
        PurchaseBillResponse response = new PurchaseBillResponse();
        response.setId(bill.getId());
        response.setBillNumber(bill.getBillNumber());
        response.setBillDate(bill.getBillDate());
        response.setSubTotalAmount(bill.getSubTotalAmount());
        response.setDiscountAmount(bill.getDiscountAmount());
        response.setTaxAmount(bill.getTaxAmount());
        response.setTotalAmount(bill.getTotalAmount());
        response.setPaidAmount(bill.getPaidAmount());
        response.setDueAmount(bill.getDueAmount());
        response.setStatus(bill.getStatus() != null ? bill.getStatus() : PurchaseStatus.ACTIVE);
        response.setCancelReason(bill.getCancelReason());
        response.setCancelledAt(bill.getCancelledAt());
        response.setNotes(bill.getNotes());
        response.setCreatedAt(bill.getCreatedAt());

        if (bill.getSupplier() != null) {
            response.setSupplierId(bill.getSupplier().getId());
            response.setSupplierName(bill.getSupplier().getName());
            response.setSupplierCode(bill.getSupplier().getSupplierCode());
        }

        if (bill.getItems() != null) {
            response.setItems(bill.getItems().stream()
                    .map(PurchaseBillResponseMapper::toItemResponse)
                    .toList());
        }

        if (bill.getPayments() != null) {
            response.setPayments(bill.getPayments().stream()
                    .map(PurchaseBillResponseMapper::toPaymentResponse)
                    .toList());
        }

        return response;
    }

    private static PurchaseBillResponse.Item toItemResponse(PurchaseBillItem item) {
        PurchaseBillResponse.Item response = new PurchaseBillResponse.Item();
        if (item.getProduct() != null) {
            response.setProductId(item.getProduct().getId());
        }
        response.setBarcode(item.getBarcode());
        response.setProductName(item.getProductName());
        response.setQuantity(item.getQuantity());
        response.setPurchasePrice(item.getPurchasePrice());
        response.setSellingPrice(item.getSellingPrice());
        response.setLineTotal(item.getLineTotal());
        return response;
    }

    private static PurchaseBillResponse.Payment toPaymentResponse(PurchasePayment payment) {
        PurchaseBillResponse.Payment response = new PurchaseBillResponse.Payment();
        response.setMethod(payment.getMethod());
        response.setAmount(payment.getAmount());
        response.setReference(payment.getReference());
        response.setCreatedAt(payment.getCreatedAt());
        return response;
    }
}
