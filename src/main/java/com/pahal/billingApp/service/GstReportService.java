package com.pahal.billingApp.service;
import com.pahal.billingApp.dto.GstDTO;
import com.pahal.billingApp.entity.GstDocument;
import com.pahal.billingApp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class GstReportService {
    private final GstDocumentRepository documents;
    private final BillRepository bills;
    private final PurchaseBillRepository purchases;
    public GstReportService(GstDocumentRepository documents, BillRepository bills, PurchaseBillRepository purchases) {
        this.documents = documents; this.bills = bills; this.purchases = purchases;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @com.pahal.billingApp.licensing.RequiresFeature(com.pahal.billingApp.licensing.Feature.GST_ACCOUNTING)
    public GstDTO.Report report(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) throw new IllegalArgumentException("Choose a valid date range.");
        String tenant = StockService.requireTenant();
        List<GstDocument> rows = documents.findByTenantIdAndDocumentDateBetweenOrderByDocumentDateAscIdAsc(tenant, from, to).stream()
                .filter(doc -> !doc.getReportingDate().isBefore(from) && !doc.getReportingDate().isAfter(to))
                .sorted(Comparator.comparing(GstDocument::getReportingDate).thenComparing(GstDocument::getId)).toList();
        List<GstDTO.ExceptionRow> exceptions = new ArrayList<>();
        bills.findLegacyGst(tenant, from.atStartOfDay(), to.plusDays(1).atStartOfDay()).forEach(bill -> exceptions.add(new GstDTO.ExceptionRow("sale:" + bill.getId(), "LEGACY_SALE", bill.getId(), "INV-" + String.format("%08d", bill.getId()), bill.getCreatedAt().toLocalDate(), "No immutable GST snapshot. Historical components/classification cannot be inferred.")));
        purchases.findLegacyGst(tenant, from, to).forEach(bill -> exceptions.add(new GstDTO.ExceptionRow("purchase:" + bill.getId(), "LEGACY_PURCHASE", bill.getId(), bill.getBillNumber(), bill.getBillDate(), "No line GST snapshot. Header tax is insufficient for ITC reporting.")));
        BigDecimal[] totals = new BigDecimal[11]; Arrays.fill(totals, BigDecimal.ZERO);
        int pending = 0; Map<String, HsnTotal> hsn = new TreeMap<>();
        for (GstDocument doc : rows) {
            if (!"REGULAR".equals(doc.getRegistrationMode())) continue;
            boolean sale = doc.getDocumentType().startsWith("SALE"); int offset = sale ? 0 : 4;
            totals[offset] = totals[offset].add(doc.getTaxableAmount()); totals[offset + 1] = totals[offset + 1].add(doc.getCgstAmount());
            totals[offset + 2] = totals[offset + 2].add(doc.getSgstAmount()); totals[offset + 3] = totals[offset + 3].add(doc.getIgstAmount());
            if (!sale && "ELIGIBLE".equals(doc.getItcStatus())) {
                totals[8] = totals[8].add(doc.getCgstAmount()); totals[9] = totals[9].add(doc.getSgstAmount()); totals[10] = totals[10].add(doc.getIgstAmount());
            }
            if (!sale && "PENDING".equals(doc.getItcStatus())) {
                pending++; exceptions.add(new GstDTO.ExceptionRow("review:" + doc.getId(), "INPUT_REVIEW", doc.getSourceId(), doc.getDocumentNumber(), doc.getReportingDate(), "Input tax pending accountant review; not included in reviewed input tax. Automatic purchase-return credits are provisional until the supplier note is recorded."));
            }
            if (sale) for (GstDocument.Line line : doc.getLines()) {
                String customer = doc.getPartyGstin() == null ? "B2C" : "B2B";
                String key = String.join("|", Objects.toString(line.getHsnCode(), ""), Objects.toString(line.getUnitCode(), ""), line.getTaxCategory(), customer, line.getGstRate().toPlainString());
                hsn.computeIfAbsent(key, ignored -> new HsnTotal(line, customer)).add(line);
            }
        }
        GstDTO.Summary summary = new GstDTO.Summary(totals[0], totals[1], totals[2], totals[3], totals[4], totals[5], totals[6], totals[7], totals[8], totals[9], totals[10], pending, exceptions.size());
        return new GstDTO.Report(LocalDateTime.now(), from, to, summary, rows,
                hsn.entrySet().stream().map(entry -> entry.getValue().row(entry.getKey())).toList(), exceptions);
    }
    @Transactional(readOnly = true)
    public List<GstDocument> history(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) throw new IllegalArgumentException("Choose a valid date range.");
        return documents.findByTenantIdAndDocumentDateBetweenOrderByDocumentDateAscIdAsc(StockService.requireTenant(), from, to).stream()
                .filter(doc -> !doc.getReportingDate().isBefore(from) && !doc.getReportingDate().isAfter(to)).toList();
    }
    private static final class HsnTotal {
        final GstDocument.Line first; final String customer;
        BigDecimal qty = BigDecimal.ZERO, base = qty, cgst = qty, sgst = qty, igst = qty;
        HsnTotal(GstDocument.Line first, String customer) { this.first = first; this.customer = customer; }
        void add(GstDocument.Line line) { qty = qty.add(line.getQuantity()); base = base.add(line.getTaxableAmount()); cgst = cgst.add(line.getCgstAmount()); sgst = sgst.add(line.getSgstAmount()); igst = igst.add(line.getIgstAmount()); }
        GstDTO.Hsn row(String key) { return new GstDTO.Hsn(key, first.getHsnCode(), first.getUnitCode(), first.getTaxCategory(), customer, first.getGstRate(), qty, base, cgst, sgst, igst); }
    }
}
