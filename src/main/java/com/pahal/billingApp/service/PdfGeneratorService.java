package com.pahal.billingApp.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.entity.BillItem;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Service
public class PdfGeneratorService {

    public ByteArrayInputStream generateBillPdf(Bill bill) {
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // 1. Add Header (Store Name/Tenant ID)
            Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph header = new Paragraph("INVOICE - " + bill.getTenantId(), fontHeader);
            header.setAlignment(Element.ALIGN_CENTER);
            document.add(header);
            document.add(Chunk.NEWLINE);

            // 2. Add Bill Info
            document.add(new Paragraph("Bill ID: " + bill.getId()));
            document.add(new Paragraph("Customer: " + bill.getCustomerName()));
            document.add(new Paragraph("Contact: " + bill.getContactInfo()));
            document.add(new Paragraph("Date: " + bill.getCreatedAt().toString()));
            document.add(new Paragraph("SalesMan: " + bill.getSalesMan().getName()+"("+bill.getSalesMan().getEmployeeId()+")"));
            document.add(Chunk.NEWLINE);

            // 3. Create Table for Items
            PdfPTable table = new PdfPTable(4); // 5 columns
            table.setWidthPercentage(100);
            table.addCell("Product");
            table.addCell("Quantity");
            table.addCell("Discount(%)");
            table.addCell("Price");

            for (BillItem item : bill.getItems()) {
                table.addCell(item.getProductName());
                table.addCell(String.valueOf(item.getQuantity()));
                table.addCell(String.valueOf(item.getDiscount()+"%"));
                table.addCell(String.valueOf(item.getPriceAtSale()));
            }
            document.add(table);

            document.add(Chunk.NEWLINE);
            PdfPTable taxtable = new PdfPTable(2); // 5 columns
            taxtable.setWidthPercentage(20);
            taxtable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            taxtable.addCell("SGST");
            taxtable.addCell("9%");
            taxtable.addCell("CGST");
            taxtable.addCell("9%");
            document.add(taxtable);

            // 4. Total
            document.add(Chunk.NEWLINE);
            Paragraph total = new Paragraph("Grand Total: ₹" + bill.getTotalAmount(), fontHeader);
            total.setAlignment(Element.ALIGN_RIGHT);
            document.add(total);

            document.close();
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        return new ByteArrayInputStream(out.toByteArray());
    }
}
