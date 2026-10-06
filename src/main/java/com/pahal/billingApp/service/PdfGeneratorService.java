package com.pahal.billingApp.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.entity.BillItem;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

@Service
public class PdfGeneratorService {

    public ByteArrayInputStream generateBillPdf(Bill bill) {
        // POS receipt printers are typically 80mm wide; render a receipt-sized PDF instead of A4.
        float receiptWidthPt = 226.77f; // ~80mm in points
        int itemCount = bill.getItems() != null ? bill.getItems().size() : 0;
        float estimatedHeightPt = 350f + (itemCount * 30f);
        Rectangle receiptPage = new Rectangle(receiptWidthPt, estimatedHeightPt);

        Document document = new Document(receiptPage, 8f, 8f, 10f, 10f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // 1. Header
            Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font fontBody = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font fontBodyBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

            Paragraph header = new Paragraph("INVOICE", fontHeader);
            Paragraph header_underline = new Paragraph("---------------------------------------------", fontHeader);
            header.setAlignment(Element.ALIGN_CENTER);
            header_underline.setAlignment(Element.ALIGN_CENTER);
            document.add(header);
            document.add(header_underline);
            if (bill.getTenantId() != null && !bill.getTenantId().isBlank()) {
                Paragraph tenant = new Paragraph(bill.getTenantId(), fontBodyBold);
                tenant.setAlignment(Element.ALIGN_CENTER);
                document.add(tenant);
                Paragraph tenant_address = new Paragraph("Electronic City, Bangalore-560100, Contact - 7318733122", fontBody);
                tenant_address.setAlignment(Element.ALIGN_CENTER);
                document.add(tenant_address);
            }
            document.add(Chunk.NEWLINE);

            // 2. Add Bill Info
            document.add(new Paragraph("Bill: " + bill.getId(), fontBody));
            if (bill.getCreatedAt() != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
                document.add(new Paragraph("Date: " + bill.getCreatedAt().format(formatter), fontBody));
            }
            if (bill.getSalesMan() != null) {
                String salesmanName = bill.getSalesMan().getName();
                boolean isSelfService = salesmanName != null
                        && salesmanName.trim().toLowerCase(Locale.ROOT).equals("self service");
                if (!isSelfService) {
                    String salesmanLine = "Salesman: " + (salesmanName != null ? salesmanName : "-");
                    if (bill.getSalesMan().getEmployeeId() != null) {
                        salesmanLine += " (" + bill.getSalesMan().getEmployeeId() + ")";
                    }
                    document.add(new Paragraph(salesmanLine, fontBody));
                }
            }
            if (bill.getCustomerName() != null && !bill.getCustomerName().isBlank()) {
                document.add(new Paragraph("Customer Name: "+bill.getCustomerName(), fontBodyBold));
                if (bill.getContactInfo() != null && !bill.getContactInfo().isBlank()) {
                    document.add(new Paragraph("Contact Number: "+bill.getContactInfo(), fontBody));
                }
            } else if (bill.getContactInfo() != null && !bill.getContactInfo().isBlank()) {
                document.add(new Paragraph("Contact Number: "+bill.getContactInfo(), fontBodyBold));
            }
            Paragraph line2 = new Paragraph("____________________________________", fontBody);
            line2.setAlignment(Element.ALIGN_CENTER);
            document.add(line2);

            // 3. Items
            int totalQty = 0;
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3.0f, 0.6f, 0.8f, 0.9f});
            table.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.getDefaultCell().setBorder(Rectangle.NO_BORDER);
            table.addCell(new Phrase("Item", fontBodyBold));
            table.addCell(new Phrase("Qty", fontBodyBold));
            table.addCell(new Phrase("Rate", fontBodyBold));
            table.addCell(new Phrase("Amt", fontBodyBold));
            if (bill.getItems() != null) {

                for (BillItem item : bill.getItems()) {
                    double qty = item.getQuantity() != null ? item.getQuantity() : 0.0;
                    double unit = item.getPriceAtSale() != null ? item.getPriceAtSale() : 0.0;
                    double ratePerunit = item.getUnitSellingPrice() != null ? item.getUnitSellingPrice() : 0.0;
//                    double discountedUnit = unit - (unit * ratePerunit / 100.0);
                    double lineTotal = ratePerunit * qty;
                    totalQty = totalQty + (int) qty;

                    String productName = item.getProductName() != null ? item.getProductName() : "-";
                    if (item.getHsnCode() != null && !item.getHsnCode().isBlank()) {
                        productName += "\nHSN: " + item.getHsnCode();
                    }
                    table.addCell(new Phrase(productName, fontBody));
                    table.addCell(new Phrase(String.valueOf(qty), fontBody));
                    table.addCell(new Phrase(String.valueOf(ratePerunit), fontBody));
                    table.addCell(new Phrase(String.format("%.2f", lineTotal), fontBody));
                }
            }
            document.add(table);

//            document.add(new Paragraph("--------------------------------", fontBody));
//            PdfPTable taxtable = new PdfPTable(2);
//            taxtable.setWidthPercentage(100);
//            taxtable.setHorizontalAlignment(Element.ALIGN_RIGHT);
//            taxtable.getDefaultCell().setBorder(Rectangle.NO_BORDER);
//            taxtable.addCell(new Phrase("SGST", fontBody));
//            taxtable.addCell(new Phrase("9%", fontBody));
//            taxtable.addCell(new Phrase("CGST", fontBody));
//            taxtable.addCell(new Phrase("9%", fontBody));
//            document.add(taxtable);

            // 4. Total
            Paragraph line3 = new Paragraph("____________________________________", fontBody);
            line3.setAlignment(Element.ALIGN_CENTER);
            document.add(line3);

            boolean gstApplied = bill.getGstApplied() != null && bill.getGstApplied();
            Double subTotal = bill.getSubTotalAmount();
            Double gstAmount = bill.getGstAmount();
            Double gstRate = bill.getGstRate();

            if (subTotal != null) {
                Paragraph subTotalLine = new Paragraph("SUBTOTAL: \u20B9" + String.format("%.2f", subTotal), fontBodyBold);
                subTotalLine.setAlignment(Element.ALIGN_RIGHT);
                document.add(subTotalLine);
            }
            if ( totalQty != 0) {
                Paragraph subTotalQty = new Paragraph("Qty: \u20B9" + String.format(String.valueOf(totalQty)), fontBodyBold);
                subTotalQty.setAlignment(Element.ALIGN_LEFT);
                document.add(subTotalQty);
            }

            if (gstApplied) {
                double pct = (gstRate != null ? gstRate : 0.0) * 100.0;
                Paragraph gstLine = new Paragraph("GST" + (pct > 0 ? " (" + String.format("%.0f", pct) + "%)" : "") + ": \u20B9" + String.format("%.2f", gstAmount != null ? gstAmount : 0.0), fontBodyBold);
                gstLine.setAlignment(Element.ALIGN_RIGHT);
                document.add(gstLine);
            } else {
                Paragraph gstLine = new Paragraph("GST: Not Applied", fontBody);
                gstLine.setAlignment(Element.ALIGN_RIGHT);
                document.add(gstLine);
            }

            Double instantDiscount = bill.getInstantDiscountAmount();
            if (instantDiscount != null && instantDiscount > 0.0001) {
                Paragraph discountLine = new Paragraph("DISCOUNT: -\u20B9" + String.format("%.2f", instantDiscount), fontBodyBold);
                discountLine.setAlignment(Element.ALIGN_RIGHT);
                document.add(discountLine);
            }

            Paragraph total = new Paragraph("TOTAL: \u20B9" + String.format("%.2f", bill.getTotalAmount() != null ? bill.getTotalAmount() : 0.0), fontHeader);
            total.setAlignment(Element.ALIGN_RIGHT);
            document.add(total);

            document.add(Chunk.NEWLINE);
            Paragraph footer = new Paragraph("Thank you!", fontBodyBold);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        return new ByteArrayInputStream(out.toByteArray());
    }
}
