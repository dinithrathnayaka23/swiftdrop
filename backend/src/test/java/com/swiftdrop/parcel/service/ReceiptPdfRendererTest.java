package com.swiftdrop.parcel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.parcel.dto.response.AddressResponse;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

class ReceiptPdfRendererTest {

    private final ReceiptPdfRenderer renderer = new ReceiptPdfRenderer();

    private static ReceiptContent receipt(String orderReference, BigDecimal lengthCm) {
        AddressResponse pickup = new AddressResponse("Ayesha Khan", "+94 77 123 4567", "123, Flower Road", null,
                "Colombo 07", "Western", "00700");
        AddressResponse dropoff = new AddressResponse("Hassan Ali", "+94 76 987 6543", "45, Galle Road", "Flat 2",
                "Galle", "Southern", "80000");
        return new ReceiptContent("SDR-7KQ2MX9TRB4H", Instant.parse("2026-11-04T05:00:00Z"), "SD7KQ2MX9TRB4H",
                orderReference, Instant.parse("2026-11-03T04:54:00Z"), ParcelStatusCode.OUT_FOR_DELIVERY, pickup,
                dropoff, ParcelType.FRAGILE, "Glassware", new BigDecimal("2.500"), lengthCm, new BigDecimal("20"),
                new BigDecimal("15"), new BigDecimal("1750.00"), "LKR");
    }

    private static String text(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } finally {
            reader.close();
        }
    }

    @Test
    void drawsAOnePagePdfWithTheParcelAddressesAndFee() throws Exception {
        byte[] pdf = renderer.render(receipt("ORD-1001", new BigDecimal("30")));

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String text = text(pdf);
        assertThat(text).contains("Delivery Receipt", "SDR-7KQ2MX9TRB4H", "SD7KQ2MX9TRB4H", "ORD-1001",
                "Out for delivery", "Fragile", "Glassware", "2.5 kg", "30 x 20 x 15 cm", "Ayesha Khan",
                "Hassan Ali", "45, Galle Road", "Rs. 1,750.00", "04 Nov 2026, 10:30 AM");
    }

    @Test
    void leavesOutTheOrderReferenceWhenThereIsNone() throws Exception {
        assertThat(text(renderer.render(receipt(null, null)))).doesNotContain("Order reference")
                .contains("- x 20 x 15 cm");
    }

    @Test
    void formatsWordsAndMoney() {
        assertThat(ReceiptPdfRenderer.words("OUT_FOR_DELIVERY")).isEqualTo("Out for delivery");
        assertThat(ReceiptPdfRenderer.money(new BigDecimal("750"), "LKR")).isEqualTo("Rs. 750.00");
        assertThat(ReceiptPdfRenderer.money(new BigDecimal("12.5"), "USD")).isEqualTo("USD 12.50");
    }
}
