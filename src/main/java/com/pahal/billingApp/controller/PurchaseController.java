package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.CancelPurchaseBillRequest;
import com.pahal.billingApp.dto.BarcodeLabelPdfRequest;
import com.pahal.billingApp.dto.AddPurchasePaymentRequest;
import com.pahal.billingApp.dto.CreatePurchaseBillRequest;
import com.pahal.billingApp.dto.PurchaseBarcodeLabelResponse;
import com.pahal.billingApp.dto.PurchaseBillResponse;
import com.pahal.billingApp.dto.PurchaseReturnDTO;
import com.pahal.billingApp.service.PurchaseAmounts;
import com.pahal.billingApp.service.PurchaseReturnService;
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
    private PurchaseReturnService purchaseReturns;

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

    @Operation(summary = "Return purchase items", description = "Returns stock to the supplier and records a credit, without changing the original invoice.")
    @PostMapping("/{id}/returns")
    public PurchaseBillResponse returnItems(@PathVariable Long id, @RequestBody PurchaseReturnDTO.Request request) {
        return PurchaseBillResponseMapper.toResponse(purchaseReturns.returnItems(id, request));
    }

    @Operation(summary = "Record supplier refund", description = "Records money actually received against a purchase return credit.")
    @PostMapping("/{id}/refunds")
    public PurchaseBillResponse refund(@PathVariable Long id, @RequestBody PurchaseReturnDTO.RefundRequest request) {
        return PurchaseBillResponseMapper.toResponse(purchaseReturns.recordRefund(id, request));
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
        response.setDueAmount(bill.getStatus() == PurchaseStatus.CANCELLED ? 0.0 : bill.getDueAmount());
        response.setReturnedAmount(PurchaseAmounts.value(bill.getReturnedAmount()));
        response.setRefundedAmount(PurchaseAmounts.value(bill.getRefundedAmount()));
        response.setNetAmount(PurchaseAmounts.netAmount(bill));
        response.setSupplierCredit(PurchaseAmounts.credit(bill));
        response.setReturnStatus(PurchaseAmounts.returnStatus(bill));
        response.setReturns(bill.getReturns().stream().map(r -> new PurchaseBillResponse.Return(
                r.getId(), r.getReason(), r.getCreditAmount(), r.getActorName(), r.getCreatedAt(),
                r.getItems().stream().map(i -> new PurchaseBillResponse.ReturnItem(i.getPurchaseItemId(),
                        i.getProductId(), i.getProductName(), i.getQuantity(), i.getCreditAmount())).toList())).toList());
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
        response.setId(item.getId());
        response.setReturnedQuantity(PurchaseAmounts.value(item.getReturnedQuantity()));
        response.setRemainingQuantity(remainingQuantity(item));
        response.setReturnCreditAmount(PurchaseAmounts.cumulativeCredit(item.getPurchaseBill(), item,
                PurchaseAmounts.value(item.getQuantity())).doubleValue());
        response.setBarcode(item.getBarcode());
        response.setProductName(item.getProductName());
        response.setQuantity(item.getQuantity());
        response.setPurchasePrice(item.getPurchasePrice());
        response.setSellingPrice(item.getSellingPrice());
        response.setLineTotal(item.getLineTotal());
        response.setHsnCode(item.getHsnCode()); response.setUnitCode(item.getUnitCode()); response.setTaxCategory(item.getTaxCategory());
        response.setGstRate(item.getGstRate()); response.setTaxableAmount(item.getTaxableAmount()); response.setGstAmount(item.getGstAmount());
        response.setCgstAmount(item.getCgstAmount()); response.setSgstAmount(item.getSgstAmount()); response.setIgstAmount(item.getIgstAmount());
        return response;
    }

    private static double remainingQuantity(PurchaseBillItem item) {
        return com.pahal.billingApp.service.StockService.round2(PurchaseAmounts.value(item.getQuantity()) - PurchaseAmounts.value(item.getReturnedQuantity()));
    }

    private static PurchaseBillResponse.Payment toPaymentResponse(PurchasePayment payment) {
        PurchaseBillResponse.Payment response = new PurchaseBillResponse.Payment();
        response.setRefund(Boolean.TRUE.equals(payment.getRefund()));
        response.setActorName(payment.getActorName());
        response.setMethod(payment.getMethod());
        response.setAmount(payment.getAmount());
        response.setReference(payment.getReference());
        response.setCreatedAt(payment.getCreatedAt());
        return response;
    }
}
