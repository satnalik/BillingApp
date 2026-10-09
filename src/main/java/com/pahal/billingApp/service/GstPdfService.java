package com.pahal.billingApp.service;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.pahal.billingApp.entity.GstDocument;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.math.BigDecimal;

@Service
public class GstPdfService {
    public byte[] generate(GstDocument invoice) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(document, output); document.open();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            document.add(new Paragraph(invoice.getStoreName(), title));
            document.add(new Paragraph(value(invoice.getStoreAddress()), normal));
            document.add(new Paragraph("GSTIN: " + value(invoice.getStoreGstin()) + " | State: " + GstService.stateName(invoice.getStoreState()), normal));
            String heading = heading(invoice);
            Paragraph name = new Paragraph(heading, title); name.setSpacingBefore(20); document.add(name);
            document.add(new Paragraph("Number: " + invoice.getDocumentNumber() + "    Date: " + invoice.getDocumentDate(), bold));
            if (invoice.getOriginalDocumentNumber() != null) document.add(new Paragraph("Against invoice: " + invoice.getOriginalDocumentNumber(), normal));
            Paragraph party = new Paragraph("Party: " + value(invoice.getPartyName()) + "\n" + value(invoice.getPartyAddress()) + "\nGSTIN: " + value(invoice.getPartyGstin()) + " | Place of supply: " + GstService.stateName(invoice.getPlaceOfSupply()), normal);
            party.setSpacingBefore(14); party.setSpacingAfter(14); document.add(party);
            if (invoice.getDeliveryAddress() != null && !invoice.getDeliveryAddress().equals(invoice.getPartyAddress())) document.add(new Paragraph("Delivery address: " + invoice.getDeliveryAddress(), normal));
            if (invoice.getDocumentType().equals("SALE")) document.add(new Paragraph("Original for recipient | Reverse charge: No", normal));
            PdfPTable table = new PdfPTable(new float[]{3.3f, 1.2f, 1, 1.1f, 1.6f, 1.4f, 1.4f, 1.4f}); table.setWidthPercentage(100); table.setHeaderRows(1);
            for (String label : new String[]{"Description / HSN", "Qty / UQC", "GST %", "Category", "Taxable", "CGST", "SGST/UTGST", "IGST"}) {
                PdfPCell cell = new PdfPCell(new Phrase(label, bold)); cell.setBackgroundColor(new Color(238, 242, 247)); cell.setPadding(7); table.addCell(cell);
            }
            for (GstDocument.Line line : invoice.getLines()) for (String cell : new String[]{value(line.getProductName()) + "\nHSN " + value(line.getHsnCode()), amount(line.getQuantity()) + " " + value(line.getUnitCode()), amount(line.getGstRate().multiply(new BigDecimal("100"))), line.getTaxCategory(), amount(line.getTaxableAmount()), amount(line.getCgstAmount()), amount(line.getSgstAmount()), amount(line.getIgstAmount())}) {
                PdfPCell body = new PdfPCell(new Phrase(cell, normal)); body.setBorder(Rectangle.NO_BORDER); body.setPadding(7); table.addCell(body);
            }
            document.add(table);
            document.add(new Paragraph("Rates: intra-state CGST and SGST/UTGST are each half the listed GST rate; inter-state IGST is the full listed rate. Tax amounts are rounded per line.", normal));
            Paragraph totals = new Paragraph("Taxable value: INR " + amount(invoice.getTaxableAmount()) + "\nCGST: INR " + amount(invoice.getCgstAmount()) + "    SGST/UTGST: INR " + amount(invoice.getSgstAmount()) + "    IGST: INR " + amount(invoice.getIgstAmount()) + "\nTOTAL: INR " + amount(invoice.getTotalAmount()), bold);
            totals.setAlignment(Element.ALIGN_RIGHT); totals.setSpacingBefore(18); document.add(totals);
            if (invoice.getReason() != null) document.add(new Paragraph("Reason: " + invoice.getReason(), normal));
            if (invoice.getDocumentType().startsWith("PURCHASE")) document.add(new Paragraph("Internal register copy. Retain the original supplier invoice/note. ITC status: " + invoice.getItcStatus(), normal));
            if (invoice.getSupplierNoteNumber() != null) document.add(new Paragraph("Supplier note: " + invoice.getSupplierNoteNumber() + " | Date: " + invoice.getSupplierNoteDate() + " | Working reporting date: " + invoice.getReportingDate(), normal));
            Paragraph sign = new Paragraph("\n\nFor " + invoice.getStoreName() + "\nAuthorised signatory", normal); sign.setAlignment(Element.ALIGN_RIGHT); document.add(sign);
            document.close(); return output.toByteArray();
        } catch (DocumentException exception) { throw new IllegalStateException("Could not generate GST document.", exception); }
    }
    private static String value(String value) { return value == null ? "-" : value; }
    private static String amount(BigDecimal value) { return value == null ? "-" : value.stripTrailingZeros().toPlainString(); }
    public static String heading(GstDocument doc) {
        if (doc.getDocumentType().endsWith("CREDIT")) return "CREDIT NOTE";
        if (doc.getDocumentType().endsWith("DEBIT")) return "DEBIT NOTE";
        if (doc.getDocumentType().equals("PURCHASE")) return "PURCHASE REGISTER COPY";
        if (!doc.getDocumentType().equals("SALE")) return "RECEIPT / REGISTER COPY";
        boolean taxable = doc.getLines().stream().anyMatch(line -> "TAXABLE".equals(line.getTaxCategory()));
        boolean exempt = doc.getLines().stream().anyMatch(line -> !"TAXABLE".equals(line.getTaxCategory()));
        return !taxable ? "BILL OF SUPPLY" : exempt && doc.getPartyGstin() == null ? "INVOICE-CUM-BILL OF SUPPLY" : "TAX INVOICE";
    }
}
