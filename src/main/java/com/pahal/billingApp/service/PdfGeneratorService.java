package com.pahal.billingApp.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfArray;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfWriter;
import com.pahal.billingApp.dto.StoreProfileDTO;
import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.entity.BillItem;
import com.pahal.billingApp.entity.BillPayment;
import com.pahal.billingApp.enums.BillStatus;
import com.pahal.billingApp.enums.PaymentMethod;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class PdfGeneratorService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM uuuu, hh:mm a", Locale.ENGLISH);
    private static final float[] ITEM_COLUMN_WIDTHS = {4.15f, 1.15f, 2.0f, 2.7f};
    private final StoreProfileService storeProfileService;
    private final com.pahal.billingApp.repository.GstDocumentRepository gstDocuments;

    public PdfGeneratorService(StoreProfileService storeProfileService, com.pahal.billingApp.repository.GstDocumentRepository gstDocuments) {
        this.storeProfileService = storeProfileService;
        this.gstDocuments = gstDocuments;
    }

    public ByteArrayInputStream generateBillPdf(Bill bill) {
        int itemCount = bill.getItems() == null ? 0 : bill.getItems().size();
        float estimatedHeight = Math.min(14000f, 550f + itemCount * 65f);
        Rectangle receiptPage = new Rectangle(226.77f, estimatedHeight); // 80 mm thermal receipt
        Document document = new Document(receiptPage, 8f, 8f, 10f, 10f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();
            document.addTitle(invoiceNumber(bill.getId()));

            Font storeNameFont = font(Font.BOLD, 13);
            Font titleFont = font(Font.BOLD, 10.5f);
            Font bodyFont = font(Font.NORMAL, 8.5f);
            Font bodyBoldFont = font(Font.BOLD, 8.5f);
            Font numberFont = font(Font.NORMAL, 8);
            Font smallFont = font(Font.NORMAL, 7.5f);
            Font smallBoldFont = font(Font.BOLD, 7.5f);
            Font detailFont = font(Font.NORMAL, 7);
            detailFont.setColor(new Color(70, 70, 70));

            StoreProfileDTO store = storeProfileService.getForTenant(bill.getTenantId());
            gstDocuments.findByTenantIdAndSourceKey(bill.getTenantId(), "sale:" + bill.getId()).ifPresent(snapshot -> {
                store.setStoreName(snapshot.getStoreName()); store.setAddress(snapshot.getStoreAddress()); store.setGstin(snapshot.getStoreGstin());
                store.setInvoiceHeader(bill.getStatus() == BillStatus.ACTIVE ? ("REGULAR".equals(snapshot.getRegistrationMode()) ? GstPdfService.heading(snapshot) : "RECEIPT") : "UPDATED RECEIPT");
            });
            addCentered(document, store.getStoreName(), storeNameFont, 2f);
            addCentered(document, store.getAddress(), smallFont, 1f);
            if (hasText(store.getPhoneNumber())) {
                addCentered(document, "Phone: " + store.getPhoneNumber(), smallFont, 1f);
            }
            if (hasText(store.getGstin())) {
                addCentered(document, "GSTIN: " + store.getGstin(), smallFont, 3f);
            }

            addCentered(document, textOr(store.getInvoiceHeader(), "INVOICE"), titleFont, 3f);
            addRule(document);

            PdfPTable invoiceInfo = new PdfPTable(1);
            invoiceInfo.setWidthPercentage(100);
            addInfoRow(invoiceInfo, "Date", bill.getCreatedAt() == null ? "-" : DATE_FORMAT.format(bill.getCreatedAt()), bodyFont);
            addInfoRow(invoiceInfo, "Salesman", salesmanName(bill), bodyFont);
            document.add(invoiceInfo);

            addSmallHeading(document, "BILL TO", smallBoldFont);
            addLine(document, hasText(bill.getCustomerName()) ? bill.getCustomerName() : "Walk-in customer", bodyBoldFont, 1f);
            if (hasText(bill.getContactInfo())) addLine(document, bill.getContactInfo(), bodyFont, 1f);
            if (hasText(bill.getCustomerGstin())) addLine(document, "GSTIN: " + bill.getCustomerGstin(), smallFont, 1f);
            if (hasText(bill.getCustomerAddress())) addLine(document, bill.getCustomerAddress(), smallFont, 1f);
            if (hasText(bill.getPlaceOfSupply())) addLine(document, "Place of supply: " + GstService.stateName(bill.getPlaceOfSupply()), smallFont, 1f);
            if (hasText(bill.getDeliveryAddress()) && !bill.getDeliveryAddress().equals(bill.getCustomerAddress())) addLine(document, "Delivery: " + bill.getDeliveryAddress(), smallFont, 1f);

            PdfPTable items = new PdfPTable(4);
            items.setWidthPercentage(100);
            items.setWidths(ITEM_COLUMN_WIDTHS);
            items.setSpacingBefore(7f);
            items.setHeaderRows(1);
            items.setSplitRows(false);
            addTableHeader(items, "ITEM", smallBoldFont, Element.ALIGN_LEFT);
            addTableHeader(items, "QTY", smallBoldFont, Element.ALIGN_RIGHT);
            addTableHeader(items, "RATE", smallBoldFont, Element.ALIGN_RIGHT);
            addTableHeader(items, "AMOUNT", smallBoldFont, Element.ALIGN_RIGHT);

            double totalQuantity = 0.0;
            int lineCount = 0;
            Map<Double, Double> gstByRate = new TreeMap<>();
            if (bill.getItems() != null) {
                for (BillItem item : bill.getItems()) {
                    if (item == null) continue;

                    double soldQuantity = number(item.getQuantity());
                    double netQuantity = Math.max(0.0, number(item.getNetQuantity()));
                    double ratio = soldQuantity > 0.0001 ? netQuantity / soldQuantity : 0.0;
                    double taxableAmount = item.getFinalDiscountAmount() != null ? remainingComponent(item.getTaxableAmount(), item) : number(item.getTaxableAmount()) * ratio;
                    double gstAmount = item.getFinalDiscountAmount() != null ? remainingComponent(item.getCgstAmount(), item) + remainingComponent(item.getSgstAmount(), item) + remainingComponent(item.getIgstAmount(), item) : number(item.getGstAmount()) * ratio;
                    double discountPercent = number(item.getDiscount());
                    double gstRate = number(item.getGstRate());
                    totalQuantity += netQuantity;
                    lineCount++;

                    Phrase description = new Phrase();
                    description.add(new Chunk(displayProductName(item.getProductName()), bodyFont));
                    StringBuilder details = new StringBuilder();
                    if (hasText(item.getHsnCode())) details.append("HSN ").append(item.getHsnCode());
                    if (discountPercent > 0.0001) appendDetail(details, "Discount " + formatNumber(discountPercent, "0.##") + "%");
                    if (gstRate > 0.0001) appendDetail(details, "GST " + formatNumber(gstRate * 100.0, "0.##") + "%");
                    if (number(item.getReturnedQuantity()) > 0.0001) {
                        appendDetail(details, "Returned " + formatNumber(number(item.getReturnedQuantity()), "0.##"));
                    }
                    boolean hasDetails = details.length() > 0;
                    PdfPTable itemBlock = new PdfPTable(4);
                    itemBlock.setWidthPercentage(100);
                    itemBlock.setWidths(ITEM_COLUMN_WIDTHS);
                    addItemCell(itemBlock, description, Element.ALIGN_LEFT, hasDetails);
                    addItemCell(itemBlock, new Phrase(formatNumber(netQuantity, "0.##"), numberFont), Element.ALIGN_RIGHT, hasDetails);
                    addItemCell(itemBlock, new Phrase(formatNumber(number(item.getUnitSellingPrice()), "#,##0.00"), numberFont), Element.ALIGN_RIGHT, hasDetails);
                    addItemCell(itemBlock, new Phrase(formatNumber(taxableAmount, "#,##0.00"), numberFont), Element.ALIGN_RIGHT, hasDetails);
                    if (hasDetails) {
                        PdfPCell detailCell = new PdfPCell(new Phrase(details.toString(), detailFont));
                        detailCell.setColspan(4);
                        detailCell.setBorder(Rectangle.NO_BORDER);
                        detailCell.setPaddingLeft(0f);
                        detailCell.setPaddingRight(0f);
                        detailCell.setPaddingTop(0f);
                        detailCell.setPaddingBottom(4f);
                        itemBlock.addCell(detailCell);
                    }
                    PdfPCell completeItem = new PdfPCell(itemBlock);
                    completeItem.setColspan(4);
                    completeItem.setBorder(Rectangle.NO_BORDER);
                    completeItem.setPadding(0f);
                    items.addCell(completeItem);

                    if (gstRate > 0.0001 && gstAmount > 0.0001) {
                        double ratePercent = Math.round(gstRate * 10000.0) / 100.0;
                        gstByRate.merge(ratePercent, gstAmount, Double::sum);
                    }
                }
            }
            document.add(items);
            addLine(document, lineCount + (lineCount == 1 ? " item" : " items")
                    + "  |  Total quantity: " + formatNumber(totalQuantity, "0.##"), smallFont, 4f);

            PdfPTable totals = new PdfPTable(2);
            totals.setWidthPercentage(100);
            totals.setWidths(new float[]{1.4f, 1f});
            totals.setKeepTogether(true);
            addTotalRow(totals, Boolean.TRUE.equals(bill.getGstApplied()) ? "Taxable amount" : "Subtotal",
                    money(bill.getSubTotalAmount()), bodyFont, false);

            if (bill.getTaxRegistrationMode() != null && Boolean.TRUE.equals(bill.getGstApplied())) {
                double cgst = 0, sgst = 0, igst = 0;
                for (BillItem item : bill.getItems()) {
                    cgst += remainingComponent(item.getCgstAmount(), item); sgst += remainingComponent(item.getSgstAmount(), item); igst += remainingComponent(item.getIgstAmount(), item);
                }
                if (cgst > 0) addTotalRow(totals, "CGST", money(cgst), bodyFont, false);
                if (sgst > 0) addTotalRow(totals, "SGST/UTGST", money(sgst), bodyFont, false);
                if (igst > 0) addTotalRow(totals, "IGST", money(igst), bodyFont, false);
            } else if (Boolean.TRUE.equals(bill.getGstApplied())) {
                if (gstByRate.isEmpty()) {
                    addTotalRow(totals, "GST", money(bill.getGstAmount()), bodyFont, false);
                } else {
                    for (Map.Entry<Double, Double> tax : gstByRate.entrySet()) {
                        addTotalRow(totals, "GST @ " + formatNumber(tax.getKey(), "0.##") + "%", money(tax.getValue()), bodyFont, false);
                    }
                }
            }
            double discount = number(bill.getInstantDiscountAmount());
            if (discount > 0.0001) addTotalRow(totals, bill.getTaxRegistrationMode() == null ? "Additional discount" : "Discount included", (bill.getTaxRegistrationMode() == null ? "- " : "") + money(discount), bodyFont, false);
            addTotalRow(totals, "TOTAL", money(bill.getTotalAmount()), titleFont, true);
            document.add(totals);

            addSmallHeading(document, "PAYMENT", smallBoldFont);
            Map<PaymentMethod, Double> payments = paymentsByMethod(bill);
            StringBuilder paymentModes = new StringBuilder();
            for (Map.Entry<PaymentMethod, Double> payment : payments.entrySet()) {
                if (Math.abs(payment.getValue()) > 0.0001) {
                    if (paymentModes.length() > 0) paymentModes.append(", ");
                    paymentModes.append(paymentLabel(payment.getKey()));
                }
            }
            double amountReceived = number(bill.getPaidAmount());
            double balance = Math.max(0.0, number(bill.getTotalAmount()) - amountReceived);
            if (paymentModes.length() == 0 && balance > 0.0001) paymentModes.append("CREDIT");
            addKeyValue(document, "Payment mode", paymentModes.length() == 0 ? "-" : paymentModes.toString(), bodyFont);
            addKeyValue(document, "Amount received", money(amountReceived), bodyBoldFont);
            if (amountReceived + 0.0001 < number(bill.getTotalAmount())) {
                addKeyValue(document, "Balance amount", money(balance), bodyBoldFont);
            }

            if (bill.getStatus() == BillStatus.PARTIALLY_RETURNED && hasText(bill.getReturnReason())) {
                document.add(Chunk.NEWLINE);
                addLine(document, "Return note: " + bill.getReturnReason(), smallFont, 1f);
            } else if (bill.getStatus() == BillStatus.CANCELLED && hasText(bill.getCancelReason())) {
                document.add(Chunk.NEWLINE);
                addLine(document, "Cancellation note: " + bill.getCancelReason(), smallFont, 1f);
            }

            addRule(document);
            addInvoiceBarcode(document, writer, bill.getTaxDocumentNumber() == null ? invoiceNumber(bill.getId()) : bill.getTaxDocumentNumber(), smallFont);
            if (hasText(store.getInvoiceFooter())) {
                addCentered(document, store.getInvoiceFooter(), bodyBoldFont, 2f);
            }
            addCentered(document, "Please retain this invoice for your records.", smallFont, 0f);
            addCentered(document, "Cashier: " + textOr(bill.getCashierName(), "Not recorded"), smallFont, 0f);
            float contentBottom = writer.getVerticalPosition(true);
            document.close();
            return new ByteArrayInputStream(trimReceipt(out.toByteArray(), contentBottom));
        } catch (DocumentException | IOException exception) {
            throw new IllegalStateException("Could not generate bill PDF", exception);
        }
    }

    private static Font font(int style, float size) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style);
    }
    private static double remainingComponent(Double amount, BillItem item) {
        if (item.getQuantity() == null || item.getQuantity() <= 0) return 0;
        java.math.BigDecimal original = GstCalculator.money(java.math.BigDecimal.valueOf(number(amount)));
        return original.subtract(original.multiply(java.math.BigDecimal.valueOf(number(item.getReturnedQuantity())))
                .divide(java.math.BigDecimal.valueOf(item.getQuantity()), 2, java.math.RoundingMode.HALF_UP)).doubleValue();
    }

    private static void addCentered(Document document, String value, Font font, float spacingAfter) throws DocumentException {
        if (!hasText(value)) return;
        Paragraph paragraph = new Paragraph(value.trim(), font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingAfter(spacingAfter);
        document.add(paragraph);
    }

    private static void addLine(Document document, String value, Font font, float spacingAfter) throws DocumentException {
        if (!hasText(value)) return;
        Paragraph paragraph = new Paragraph(value.trim(), font);
        paragraph.setSpacingAfter(spacingAfter);
        document.add(paragraph);
    }

    private static void addSmallHeading(Document document, String value, Font font) throws DocumentException {
        Paragraph paragraph = new Paragraph(value, font);
        paragraph.setSpacingBefore(6f);
        paragraph.setSpacingAfter(2f);
        document.add(paragraph);
    }

    private static void addRule(Document document) throws DocumentException {
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        rule.setSpacingBefore(5f);
        rule.setSpacingAfter(5f);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderWidthBottom(0.5f);
        cell.setBorderColorBottom(Color.BLACK);
        cell.setPadding(0f);
        cell.setFixedHeight(1f);
        rule.addCell(cell);
        document.add(rule);
    }

    private static void addInfoRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(label + ": " + textOr(value, "-"), font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setPaddingTop(2f);
        cell.setPaddingBottom(2f);
        cell.setPaddingLeft(0f);
        cell.setPaddingRight(0f);
        table.addCell(cell);
    }

    private static void addInvoiceBarcode(Document document, PdfWriter writer, String billNumber, Font labelFont)
            throws DocumentException {
        if (billNumber == null || billNumber.isBlank()) return;

        Barcode128 barcode = new Barcode128();
        barcode.setCodeType(Barcode128.CODE128);
        barcode.setCode(billNumber);
        barcode.setFont(null); // Print the readable invoice number as a separate line.
        barcode.setBarHeight(32f);
        barcode.setX(1f);
        // Reserve blank space on both sides so the first and last bars remain scannable.
        float availableWidth = document.right() - document.left() - 24f;
        barcode.setX(Math.min(1f, availableWidth / barcode.getBarcodeSize().getWidth()));
        Image image = barcode.createImageWithBarcode(writer.getDirectContent(), Color.BLACK, Color.BLACK);
        image.setAlignment(Element.ALIGN_CENTER);
        image.setWidthPercentage(0f); // Preserve the module width and bar height inside the table cell.

        PdfPTable block = new PdfPTable(1);
        block.setWidthPercentage(100);
        block.setKeepTogether(true);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingLeft(12f);
        cell.setPaddingRight(12f);
        cell.setPaddingTop(5f);
        cell.setPaddingBottom(7f);
        cell.addElement(image);
        Paragraph label = new Paragraph(billNumber, labelFont);
        label.setAlignment(Element.ALIGN_CENTER);
        label.setSpacingBefore(4f);
        label.setLeading(10f);
        cell.addElement(label);
        block.addCell(cell);
        document.add(block);
    }

    private static void addTableHeader(PdfPTable table, String value, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColorBottom(Color.BLACK);
        cell.setBorderWidthBottom(0.5f);
        cell.setPaddingLeft(0f);
        cell.setPaddingRight(0f);
        cell.setPaddingTop(3f);
        cell.setPaddingBottom(4f);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private static void addItemCell(PdfPTable table, Phrase value, int alignment, boolean hasDetails) {
        PdfPCell cell = new PdfPCell(value);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingLeft(0f);
        cell.setPaddingRight(alignment == Element.ALIGN_LEFT ? 4f : 0f);
        cell.setPaddingTop(4f);
        cell.setPaddingBottom(hasDetails ? 1f : 4f);
        cell.setLeading(0f, 1.15f);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        table.addCell(cell);
    }

    private static void addTotalRow(PdfPTable table, String label, String value, Font font, boolean strong) {
        Font selectedFont = strong ? font(Font.BOLD, 12) : font;
        PdfPCell labelCell = new PdfPCell(new Phrase(label, selectedFont));
        PdfPCell valueCell = new PdfPCell(new Phrase(value, selectedFont));
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setBorder(strong ? Rectangle.TOP : Rectangle.NO_BORDER);
        valueCell.setBorder(strong ? Rectangle.TOP : Rectangle.NO_BORDER);
        if (strong) {
            labelCell.setBorderColorTop(Color.BLACK);
            valueCell.setBorderColorTop(Color.BLACK);
            labelCell.setBorderWidthTop(0.6f);
            valueCell.setBorderWidthTop(0.6f);
        }
        labelCell.setPaddingLeft(0f);
        valueCell.setPaddingRight(0f);
        labelCell.setPaddingTop(strong ? 6f : 3f);
        valueCell.setPaddingTop(strong ? 6f : 3f);
        labelCell.setPaddingBottom(strong ? 5f : 3f);
        valueCell.setPaddingBottom(strong ? 5f : 3f);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private static void addKeyValue(Document document, String label, String value, Font font) throws DocumentException {
        PdfPTable row = new PdfPTable(2);
        row.setWidthPercentage(100);
        row.setWidths(new float[]{1f, 1f});
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        labelCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setPaddingTop(2f);
        labelCell.setPaddingBottom(2f);
        labelCell.setPaddingLeft(0f);
        valueCell.setPaddingTop(2f);
        valueCell.setPaddingBottom(2f);
        valueCell.setPaddingRight(0f);
        row.addCell(labelCell);
        row.addCell(valueCell);
        document.add(row);
    }

    private static Map<PaymentMethod, Double> paymentsByMethod(Bill bill) {
        Map<PaymentMethod, Double> result = new EnumMap<>(PaymentMethod.class);
        if (bill.getPayments() == null) return result;
        for (BillPayment payment : bill.getPayments()) {
            if (payment == null || payment.getMethod() == null) continue;
            result.merge(payment.getMethod(), number(payment.getAmount()), Double::sum);
        }
        return result;
    }

    private static String invoiceNumber(Long id) {
        return id == null ? "-" : String.format(Locale.ROOT, "INV-%08d", id);
    }

    private static String salesmanName(Bill bill) {
        if (bill.getSalesMan() == null || !hasText(bill.getSalesMan().getName())) return "-";
        String name = bill.getSalesMan().getName().trim();
        if ("self service".equalsIgnoreCase(name)) return "-";
        String employeeId = bill.getSalesMan().getEmployeeId();
        return hasText(employeeId) ? name + " (" + employeeId + ")" : name;
    }

    private static String paymentLabel(PaymentMethod method) {
        return switch (method) {
            case CASH -> "Cash";
            case UPI -> "UPI";
            case CARD -> "Card";
            case CREDIT -> "Credit";
        };
    }

    private static String money(Double amount) {
        return "Rs. " + formatNumber(number(amount), "#,##0.00");
    }

    private static String formatNumber(double value, String pattern) {
        DecimalFormat format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(value);
    }

    private static double number(Double value) {
        return value == null || !Double.isFinite(value) ? 0.0 : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String textOr(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static String displayProductName(String value) {
        return textOr(value, "Item").replace('_', ' ').replaceAll("\\s+", " ");
    }

    /** Remove unused paper beneath the final footer, preserving all printed content. */
    private static byte[] trimReceipt(byte[] pdf, float contentBottom) throws IOException, DocumentException {
        PdfReader reader = new PdfReader(pdf);
        try {
            int lastPage = reader.getNumberOfPages();
            Rectangle page = reader.getPageSize(lastPage);
            float bottom = Math.max(page.getBottom(), contentBottom - 10f);
            PdfArray box = new PdfArray(new float[]{page.getLeft(), bottom, page.getRight(), page.getTop()});
            reader.getPageN(lastPage).put(PdfName.MEDIABOX, box);
            reader.getPageN(lastPage).put(PdfName.CROPBOX, box);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PdfStamper stamper = new PdfStamper(reader, output);
            stamper.close();
            return output.toByteArray();
        } finally {
            reader.close();
        }
    }

    private static void appendDetail(StringBuilder details, String value) {
        if (details.length() > 0) details.append(" | ");
        details.append(value);
    }
}
