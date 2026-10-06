
package com.pahal.billingApp.service;

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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class BarcodeLabelPdfService {

    /*
     * TechNova NovaJet 21L
     *
     * Label size:
     * Width  = 63.5 mm
     * Height = 38.0 mm
     *
     * A4:
     * 210 mm x 297 mm
     *
     * Layout:
     * 3 columns x 7 rows = 21 labels
     *
     * TechNova dimensions:
     * Top margin       = 15.5 mm
     * Side margin      = 7.0 mm
     * Horizontal pitch = 66.0 mm
     * Vertical pitch   = 38.0 mm
     */

    private static final float MM_TO_PT = 2.8346457f;

    private static final float LABEL_WIDTH_MM = 63.5f;
    private static final float LABEL_HEIGHT_MM = 38.0f;

    private static final float LEFT_MARGIN_MM = 7.0f;
    private static final float RIGHT_MARGIN_MM = 5.0f;
    private static final float TOP_MARGIN_MM = 15.5f;
    private static final float BOTTOM_MARGIN_MM = 15.5f;
    private static final float TEXT_SPACING = 1.5f;

    public ByteArrayInputStream generatePurchaseLabelsPdf(
            PurchaseBarcodeLabelResponse labels,
            BarcodeLabelPdfRequest request) {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Document document = new Document(
                PageSize.A4,
                mm(LEFT_MARGIN_MM),
                mm(RIGHT_MARGIN_MM),
                mm(TOP_MARGIN_MM),
                mm(BOTTOM_MARGIN_MM)
        );

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);

            document.open();

            /*
             * 3 labels across.
             */
            PdfPTable table = new PdfPTable(3);

            /*
             * Each column is 63.5 mm wide.
             */
            table.setTotalWidth(mm(LABEL_WIDTH_MM * 3));
            table.setLockedWidth(true);

            table.setWidths(new float[]{
                    LABEL_WIDTH_MM,
                    LABEL_WIDTH_MM,
                    LABEL_WIDTH_MM
            });

            /*
             * No extra spacing between rows.
             */
            table.setSpacingBefore(0);
            table.setSpacingAfter(0);
            table.setWidthPercentage(100);

            Map<Long, Integer> requestedCounts = toRequestedCounts(request);

            int cellCount = 0;

            if (labels != null && labels.getItems() != null) {

                for (PurchaseBarcodeLabelResponse.Item item : labels.getItems()) {

                    int labelsToPrint = requestedCounts.getOrDefault(
                            item.getProductId(),
                            defaultCount(item)
                    );

                    for (int i = 0; i < labelsToPrint; i++) {

                        table.addCell(buildLabelCell(writer, item));

                        cellCount++;
                    }
                }
            }

            /*
             * Complete the last row with empty cells.
             *
             * This keeps the remaining labels blank instead
             * of disturbing the 3-column layout.
             */
            int remainder = cellCount % 3;

            if (remainder != 0) {

                for (int i = remainder; i < 3; i++) {
                    table.addCell(emptyCell());
                }
            }

            document.add(table);

            document.close();

        } catch (DocumentException e) {

            throw new RuntimeException(
                    "Failed to generate barcode label PDF",
                    e
            );
        }

        return new ByteArrayInputStream(out.toByteArray());
    }


    /**
     * Creates one 63.5 mm x 38 mm label.
     *
     * Layout:
     *
     * MRP: Rs 200.00
     * MFD: 12/09/2026
     *
     *      BARCODE
     *  |||||||||||||||
     *     123456789
     */
    private PdfPCell buildLabelCell(
            PdfWriter writer,
            PurchaseBarcodeLabelResponse.Item item) {

        PdfPCell cell = new PdfPCell();

        /*
         * Exact TechNova 21L label height.
         */
        cell.setFixedHeight(mm(LABEL_HEIGHT_MM));

        /*
         * Small internal padding.
         */
        cell.setPadding(3f);

        cell.setBorder(Rectangle.NO_BORDER);

        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);


        /*
         * Fonts
         */

        Font productNameFont = FontFactory.getFont(
                FontFactory.HELVETICA_BOLD,
                8
        );

        Font mrpFont = FontFactory.getFont(
                FontFactory.HELVETICA_BOLD,
                8
        );

        Font mfdFont = FontFactory.getFont(
                FontFactory.HELVETICA,
                7
        );

        Font barcodeTextFont = FontFactory.getFont(
                FontFactory.HELVETICA,
                6.5f
        );


        String productName = item.getProductName();

        Paragraph pName = new Paragraph(
                productName,
                productNameFont
        );

        pName.setAlignment(Element.ALIGN_CENTER);
        pName.setLeading(8f);
        pName.setSpacingBefore(0f);
        pName.setSpacingAfter(TEXT_SPACING);

        cell.addElement(pName);

        /*
         * -------------------------
         * MRP
         * -------------------------
         *
         * Example:
         * MRP: Rs 200.00
         */
        String mrpText = "MRP: Rs " + formatAmount(item.getMrp());

        Paragraph mrp = new Paragraph(
                mrpText,
                mrpFont
        );

        mrp.setAlignment(Element.ALIGN_CENTER);
        mrp.setLeading(8f);
        mrp.setSpacingBefore(2f);
        mrp.setSpacingAfter(TEXT_SPACING);

        cell.addElement(mrp);


        /*
         * -------------------------
         * MFD
         * -------------------------
         *
         * Date on which barcode PDF
         * is generated.
         */
//        String currentDate = new SimpleDateFormat(
//                "dd MMM yy"
//        ).format(new Date());

        String currentDate = "13 Sep 26";

        Paragraph mfd = new Paragraph(
                "MFD: " + currentDate,
                mfdFont
        );

        mfd.setAlignment(Element.ALIGN_CENTER);
        mfd.setLeading(8f);
        mfd.setSpacingBefore(2f);
        mfd.setSpacingAfter(2f);

        cell.addElement(mfd);


        /*
         * -------------------------
         * BARCODE
         * -------------------------
         */
        Barcode128 barcode = new Barcode128();

        barcode.setCodeType(Barcode128.CODE128);

        barcode.setCode(
                nullToDash(item.getBarcode())
        );

        /*
         * Barcode height.
         */
        barcode.setBarHeight(10f);

        /*
         * Width of individual bars.
         */
        barcode.setX(0.8f);

        /*
         * Barcode text size.
         *
         * We are separately printing the barcode
         * number below, so keep this small.
         */
        barcode.setSize(5f);

        Image image = barcode.createImageWithBarcode(
                writer.getDirectContent(),
                null,
                null
        );

        /*
         * Maximum barcode size inside
         * 63.5 x 38 mm label.
         */
        image.scaleToFit(
                mm(50f),
                mm(18f)
        );

        image.setAlignment(Image.ALIGN_CENTER);

        cell.addElement(image);


        /*
         * Print barcode number below barcode.
         */
//        Paragraph code = new Paragraph(
//                nullToDash(item.getBarcode()),
//                barcodeTextFont
//        );
//
//        code.setAlignment(Element.ALIGN_CENTER);
//        code.setLeading(7f);
//
//        cell.addElement(code);


        return cell;
    }


    /**
     * Creates an empty label.
     */
    private PdfPCell emptyCell() {

        PdfPCell cell = new PdfPCell(
                new Phrase("")
        );

        cell.setFixedHeight(
                mm(LABEL_HEIGHT_MM)
        );

        cell.setBorder(
                Rectangle.NO_BORDER
        );

        return cell;
    }


    /**
     * Converts request into:
     *
     * productId -> number of labels
     */
    private Map<Long, Integer> toRequestedCounts(
            BarcodeLabelPdfRequest request) {

        Map<Long, Integer> counts = new HashMap<>();

        if (request == null ||
                request.getItems() == null) {

            return counts;
        }

        for (BarcodeLabelPdfRequest.Item item :
                request.getItems()) {

            if (item == null ||
                    item.getProductId() == null) {

                continue;
            }

            int count =
                    item.getLabelsToPrint() != null
                            ? item.getLabelsToPrint()
                            : 0;

            counts.put(
                    item.getProductId(),
                    Math.max(count, 0)
            );
        }

        return counts;
    }


    /**
     * Uses item's default label count.
     */
    private int defaultCount(
            PurchaseBarcodeLabelResponse.Item item) {

        return Math.max(
                item.getLabelsToPrint() != null
                        ? item.getLabelsToPrint()
                        : 0,
                0
        );
    }


    /**
     * Converts null/blank strings to "-".
     */
    private String nullToDash(String value) {

        return value != null &&
                !value.isBlank()
                ? value
                : "-";
    }


    /**
     * Formats MRP:
     *
     * 200 -> 200.00
     * 99.5 -> 99.50
     */
    private String formatAmount(Double amount) {

        return String.format(
                "%.2f",
                amount != null ? amount : 0.0
        );
    }


    /**
     * Millimetres to PDF points.
     */
    private float mm(float millimetres) {

        return millimetres * MM_TO_PT;
    }
}
