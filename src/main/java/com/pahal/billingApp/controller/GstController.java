package com.pahal.billingApp.controller;
import com.pahal.billingApp.dto.*;
import com.pahal.billingApp.entity.*;
import com.pahal.billingApp.service.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController @RequestMapping("/api/gst") @Tag(name = "GST", description = "Local GST records and accountant working reports; no portal filing")
public class GstController {
    private final GstService gst; private final GstReportService reports; private final GstPdfService pdf;
    public GstController(GstService gst, GstReportService reports, GstPdfService pdf) { this.gst = gst; this.reports = reports; this.pdf = pdf; }
    @GetMapping("/settings") public GstSettings settings() { return gst.settings(); }
    @GetMapping("/history") public List<GstDocument> history(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) { return reports.history(from, to); }
    @PutMapping("/settings") public GstSettings save(@RequestBody GstSettings request) { return gst.saveSettings(request); }
    @PostMapping("/quote") public GstDTO.Quote quote(@RequestBody CreateBillRequest request) { return gst.quote(request); }
    @PostMapping("/purchase-quote") public GstDTO.Quote purchaseQuote(@RequestBody CreatePurchaseBillRequest request) { return gst.purchaseQuote(request); }
    @PutMapping("/products/{id}/tax") public Product productTax(@PathVariable Long id, @RequestBody GstDTO.ProductTax request) { return gst.saveProductTax(id, request); }
    @GetMapping("/report") public GstDTO.Report report(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) { return reports.report(from, to); }
    @GetMapping("/documents/{id}") public GstDocument document(@PathVariable Long id) { return gst.document(id); }
    @PatchMapping("/documents/{id}/review") public GstDocument review(@PathVariable Long id, @RequestBody GstDTO.Review request) { return gst.review(id, request); }
    @PostMapping("/adjustments") public GstDocument adjustment(@RequestBody GstDTO.Adjustment request) { return gst.manualAdjustment(request); }
    @GetMapping("/periods") public List<GstPeriod> periods() { return gst.periods(); }
    @PutMapping("/periods") public GstPeriod period(@RequestBody GstDTO.PeriodChange request) { return gst.changePeriod(request); }
    @GetMapping("/audit") public List<GstAudit> audit() { return gst.audit(); }
    @GetMapping("/documents/{id}/pdf") public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        GstDocument doc = gst.document(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(doc.getDocumentNumber().replace('/', '-') + ".pdf").build().toString()).body(pdf.generate(doc));
    }
}
