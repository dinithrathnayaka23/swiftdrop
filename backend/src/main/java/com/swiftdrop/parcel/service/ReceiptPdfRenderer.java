package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.dto.response.AddressResponse;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.StringJoiner;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

/**
 * Draws a one-page A4 delivery receipt with OpenPDF. Uses the built-in Helvetica font, so text
 * outside Latin-1 (for example Sinhala or Tamil names) is not shown.
 */
@Component
class ReceiptPdfRenderer {

    private static final ZoneId SRI_LANKA = ZoneId.of("Asia/Colombo");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH).withZone(SRI_LANKA);

    private static final Color BRAND = new Color(0x13, 0x67, 0x3b);
    private static final Color MUTED = new Color(0x64, 0x74, 0x8b);
    private static final Color LINE = new Color(0xe2, 0xe8, 0xf0);

    private static final Font TITLE = new Font(Font.HELVETICA, 22, Font.BOLD, BRAND);
    private static final Font HEADING = new Font(Font.HELVETICA, 12, Font.BOLD, Color.BLACK);
    private static final Font LABEL = new Font(Font.HELVETICA, 9, Font.NORMAL, MUTED);
    private static final Font VALUE = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font VALUE_BOLD = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
    private static final Font TOTAL = new Font(Font.HELVETICA, 13, Font.BOLD, BRAND);

    byte[] render(ReceiptContent receipt) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 48, 48);
        PdfWriter.getInstance(document, out);
        document.addTitle("SwiftDrop receipt " + receipt.receiptNumber());
        document.addCreator("SwiftDrop");
        document.open();

        document.add(header(receipt));
        document.add(spacer(18));
        document.add(section("Parcel", details(receipt)));
        document.add(spacer(12));
        document.add(addresses(receipt.pickup(), receipt.dropoff()));
        document.add(spacer(12));
        document.add(section("Charges", charges(receipt)));
        document.add(spacer(24));

        Paragraph footer = new Paragraph("Thank you for shipping with SwiftDrop. Track your parcel any time at "
                + "/track/" + receipt.trackingNumber() + ".", LABEL);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);

        document.close();
        return out.toByteArray();
    }

    private static PdfPTable header(ReceiptContent receipt) {
        PdfPTable table = new PdfPTable(new float[] {3, 2});
        table.setWidthPercentage(100);
        PdfPCell brand = cell(null);
        brand.addElement(new Paragraph("SwiftDrop", TITLE));
        brand.addElement(new Paragraph("Delivery Receipt", HEADING));
        table.addCell(brand);

        PdfPCell meta = cell(null);
        meta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        meta.addElement(rightAligned("Receipt No.", LABEL));
        meta.addElement(rightAligned(receipt.receiptNumber(), VALUE_BOLD));
        meta.addElement(rightAligned("Issued on", LABEL));
        meta.addElement(rightAligned(DATE_TIME.format(receipt.issuedAt()), VALUE));
        table.addCell(meta);
        return table;
    }

    private static PdfPTable details(ReceiptContent receipt) {
        PdfPTable table = new PdfPTable(new float[] {1, 2});
        table.setWidthPercentage(100);
        row(table, "Tracking number", receipt.trackingNumber());
        if (receipt.orderReference() != null) {
            row(table, "Order reference", receipt.orderReference());
        }
        row(table, "Booked on", DATE_TIME.format(receipt.bookedAt()));
        row(table, "Current status", words(receipt.status().name()));
        row(table, "Parcel type", words(receipt.parcelType().name()));
        if (receipt.contentDescription() != null) {
            row(table, "Contents", receipt.contentDescription());
        }
        row(table, "Weight", receipt.weightKg().stripTrailingZeros().toPlainString() + " kg");
        String dimensions = dimensions(receipt);
        if (dimensions != null) {
            row(table, "Dimensions (L x W x H)", dimensions);
        }
        return table;
    }

    private static PdfPTable addresses(AddressResponse pickup, AddressResponse dropoff) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.addCell(addressCell("From", pickup));
        table.addCell(addressCell("To", dropoff));
        return table;
    }

    private static PdfPTable charges(ReceiptContent receipt) {
        PdfPTable table = new PdfPTable(new float[] {3, 1});
        table.setWidthPercentage(100);
        table.addCell(cell(new Phrase("Delivery fee", VALUE)));
        table.addCell(amountCell(money(receipt.fee(), receipt.currency()), VALUE));

        PdfPCell totalLabel = cell(new Phrase("Total", TOTAL));
        totalLabel.setBorder(Rectangle.TOP);
        totalLabel.setBorderColor(LINE);
        totalLabel.setPaddingTop(8);
        table.addCell(totalLabel);
        PdfPCell total = amountCell(money(receipt.fee(), receipt.currency()), TOTAL);
        total.setBorder(Rectangle.TOP);
        total.setBorderColor(LINE);
        total.setPaddingTop(8);
        table.addCell(total);
        return table;
    }

    private static PdfPTable section(String title, PdfPTable body) {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        PdfPCell heading = cell(new Phrase(title, HEADING));
        heading.setPaddingBottom(6);
        box.addCell(heading);
        PdfPCell content = new PdfPCell(body);
        content.setBorderColor(LINE);
        content.setPadding(10);
        box.addCell(content);
        return box;
    }

    private static PdfPCell addressCell(String title, AddressResponse address) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(LINE);
        cell.setPadding(10);
        cell.addElement(new Paragraph(title, HEADING));
        cell.addElement(new Paragraph(address.contactName(), VALUE_BOLD));
        StringJoiner lines = new StringJoiner("\n");
        lines.add(address.addressLine1());
        if (address.addressLine2() != null) {
            lines.add(address.addressLine2());
        }
        lines.add(address.city() + (address.postalCode() != null ? " " + address.postalCode() : ""));
        lines.add(address.district());
        lines.add(address.contactPhone());
        cell.addElement(new Paragraph(lines.toString(), VALUE));
        return cell;
    }

    private static void row(PdfPTable table, String label, String value) {
        table.addCell(cell(new Phrase(label, LABEL)));
        table.addCell(cell(new Phrase(value, VALUE)));
    }

    private static PdfPCell cell(Phrase phrase) {
        PdfPCell cell = phrase == null ? new PdfPCell() : new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingBottom(4);
        return cell;
    }

    private static PdfPCell amountCell(String amount, Font font) {
        PdfPCell cell = cell(new Phrase(amount, font));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    private static Paragraph rightAligned(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_RIGHT);
        return paragraph;
    }

    private static Paragraph spacer(float height) {
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(height);
        return spacer;
    }

    /** "OUT_FOR_DELIVERY" → "Out for delivery". */
    static String words(String code) {
        String spaced = code.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /** "Rs. 750.00" for LKR, otherwise "USD 12.00". */
    static String money(BigDecimal amount, String currency) {
        String prefix = "LKR".equals(currency) ? "Rs." : currency;
        return prefix + " " + String.format(Locale.ENGLISH, "%,.2f", amount);
    }

    private static String dimensions(ReceiptContent receipt) {
        if (receipt.lengthCm() == null && receipt.widthCm() == null && receipt.heightCm() == null) {
            return null;
        }
        return cm(receipt.lengthCm()) + " x " + cm(receipt.widthCm()) + " x " + cm(receipt.heightCm()) + " cm";
    }

    private static String cm(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
