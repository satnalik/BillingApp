package com.pahal.billingApp.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pahal.billingApp.dto.BarcodeLabelPdfRequest;
import com.pahal.billingApp.dto.PurchaseBarcodeLabelResponse;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class BarcodeLabelPdfService {

    public ByteArrayInputStream generatePurchaseLabelsPdf(
            PurchaseBarcodeLabelResponse labels,
            BarcodeLabelPdfRequest request) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 18f, 18f, 18f, 18f);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 1f, 1f, 1f });

            Map<Long, Integer> requestedCounts = toRequestedCounts(request);
            int cellCount = 0;

            if (labels.getItems() != null) {
                for (PurchaseBarcodeLabelResponse.Item item : labels.getItems()) {
                    int labelsToPrint = requestedCounts.getOrDefault(item.getProductId(), defaultCount(item));
                    for (int i = 0; i < labelsToPrint; i++) {
                        table.addCell(buildLabelCell(writer, item));
                        cellCount++;
                    }
                }
            }

            int remainder = cellCount % 3;
            if (remainder != 0) {
                for (int i = remainder; i < 3; i++) {
                    table.addCell(emptyCell());
                }
            }

            document.add(table);
            document.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Failed to generate barcode label PDF", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    private PdfPCell buildLabelCell(PdfWriter writer, PurchaseBarcodeLabelResponse.Item item) {
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(96f);
        cell.setPadding(5f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
        Font priceFont = FontFactory.getFont(FontFactory.HELVETICA, 7);
        Font codeFont = FontFactory.getFont(FontFactory.HELVETICA, 7);

        Paragraph name = new Paragraph(nullToDash(item.getProductName()), nameFont);
        name.setAlignment(Element.ALIGN_CENTER);
        name.setLeading(9f);
        cell.addElement(name);

        String priceText = "";
        if (item.getMrp() != null) {
            priceText += "MRP: " + formatAmount(item.getMrp());
        }
        if (item.getSellingPrice() != null) {
            priceText += (priceText.isBlank() ? "" : "   ") + "Price: " + formatAmount(item.getSellingPrice());
        }
        if (!priceText.isBlank()) {
            Paragraph price = new Paragraph(priceText, priceFont);
            price.setAlignment(Element.ALIGN_CENTER);
            price.setLeading(8f);
            cell.addElement(price);
        }

        Barcode128 barcode = new Barcode128();
        barcode.setCodeType(Barcode128.CODE128);
        barcode.setCode(item.getBarcode());
        barcode.setBarHeight(28f);
        barcode.setX(0.9f);
        barcode.setSize(6f);

        Image image = barcode.createImageWithBarcode(writer.getDirectContent(), null, null);
        image.scaleToFit(145f, 32f);
        image.setAlignment(Image.ALIGN_CENTER);
        cell.addElement(image);

        Paragraph code = new Paragraph(nullToDash(item.getBarcode()), codeFont);
        code.setAlignment(Element.ALIGN_CENTER);
        code.setLeading(8f);
        cell.addElement(code);
        cell.addElement(Chunk.NEWLINE);

        return cell;
    }

    private PdfPCell emptyCell() {
        PdfPCell cell = new PdfPCell(new Phrase(""));
        cell.setFixedHeight(96f);
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private Map<Long, Integer> toRequestedCounts(BarcodeLabelPdfRequest request) {
        Map<Long, Integer> counts = new HashMap<>();
        if (request == null || request.getItems() == null) {
            return counts;
        }
        for (BarcodeLabelPdfRequest.Item item : request.getItems()) {
            if (item == null || item.getProductId() == null) continue;
            counts.put(item.getProductId(), Math.max(item.getLabelsToPrint() != null ? item.getLabelsToPrint() : 0, 0));
        }
        return counts;
    }

    private int defaultCount(PurchaseBarcodeLabelResponse.Item item) {
        return Math.max(item.getLabelsToPrint() != null ? item.getLabelsToPrint() : 0, 0);
    }

    private String nullToDash(String value) {
        return value != null && !value.isBlank() ? value : "-";
    }

    private String formatAmount(Double amount) {
        return String.format("%.2f", amount != null ? amount : 0.0);
    }
}
