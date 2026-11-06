package com.swiftdrop.tracking.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.parcel.dto.AddressDetails;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.tracking.dto.response.PublicTrackingEventResponse;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Public tracking responses carry only public-safe fields, checked on the JSON the app sends. */
@JsonTest
class PublicTrackingMapperTest {

    private static final UUID PARCEL_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID COURIER_ID = UUID.randomUUID();
    private static final UUID PRICING_RULE_ID = UUID.randomUUID();
    private static final Instant BOOKED = Instant.parse("2026-11-01T04:00:00Z");
    private static final Instant PICKED_UP = Instant.parse("2026-11-01T06:30:00Z");
    private static final Instant FAILED = Instant.parse("2026-11-02T09:15:00Z");

    @Autowired
    private JsonMapper json;

    private static AddressDetails address(String name, String phone, String line1, String city, String postalCode) {
        return new AddressDetails(UUID.randomUUID(), name, phone, line1, "Flat 4B", city, "Western", postalCode,
                UUID.randomUUID());
    }

    private static ParcelDetails parcel() {
        return new ParcelDetails(PARCEL_ID, "SD12345678", CUSTOMER_ID, null, null,
                address("Ayesha Khan", "+94771234567", "12 Galle Road", "Colombo", "00300"),
                address("Hassan Ali", "+94719876543", "45 Peradeniya Road", "Kandy", "20000"),
                ParcelType.ELECTRONICS, new BigDecimal("2.50"), new BigDecimal("30"), new BigDecimal("20"),
                new BigDecimal("10"), "Laptop", "Ring the bell twice", ParcelStatusCode.FAILED, PRICING_RULE_ID,
                new BigDecimal("750.00"), "LKR", BOOKED, Instant.parse("2026-11-03T00:00:00Z"));
    }

    /** Given newest first, so the test also checks the mapper sorts. */
    private static List<StatusHistoryEntry> history() {
        return List.of(
                new StatusHistoryEntry(UUID.randomUUID(), PARCEL_ID, ParcelStatusCode.PICKED_UP,
                        ParcelStatusCode.FAILED, COURIER_ID, "Customer phoned, not home", "Recipient not available",
                        null, null, FAILED),
                new StatusHistoryEntry(UUID.randomUUID(), PARCEL_ID, ParcelStatusCode.CREATED,
                        ParcelStatusCode.PICKED_UP, COURIER_ID, "Collected from reception", null, null,
                        "https://files.swiftdrop.local/proof/123.jpg", PICKED_UP),
                new StatusHistoryEntry(UUID.randomUUID(), PARCEL_ID, null, ParcelStatusCode.CREATED, CUSTOMER_ID,
                        null, null, "Kamala Silva", null, BOOKED));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    @Test
    void mapsPublicFieldsWithTheHistoryOldestFirst() {
        PublicTrackingResponse response = PublicTrackingMapper.toResponse(parcel(), history());

        assertThat(response.trackingNumber()).isEqualTo("SD12345678");
        assertThat(response.currentStatus()).isEqualTo(ParcelStatusCode.FAILED);
        assertThat(response.bookedAt()).isEqualTo(BOOKED);
        assertThat(response.lastUpdatedAt()).isEqualTo(FAILED);
        assertThat(response.fromCity()).isEqualTo("Colombo");
        assertThat(response.toCity()).isEqualTo("Kandy");
        assertThat(response.weightKg()).isEqualByComparingTo("2.5");
        assertThat(response.history()).containsExactly(
                new PublicTrackingEventResponse(ParcelStatusCode.CREATED, BOOKED),
                new PublicTrackingEventResponse(ParcelStatusCode.PICKED_UP, PICKED_UP),
                new PublicTrackingEventResponse(ParcelStatusCode.FAILED, FAILED));
    }

    @Test
    void withoutHistoryTheParcelsOwnUpdateTimeIsUsed() {
        PublicTrackingResponse response = PublicTrackingMapper.toResponse(parcel(), List.of());

        assertThat(response.history()).isEmpty();
        assertThat(response.lastUpdatedAt()).isEqualTo(parcel().updatedAt());
    }

    @Test
    void theJsonHasOnlyTheFieldsOfTheFrontendTrackingResult() {
        JsonNode body = json.readTree(json.writeValueAsString(PublicTrackingMapper.toResponse(parcel(), history())));

        assertThat(fieldNames(body)).containsExactlyInAnyOrder("trackingNumber", "currentStatus", "lastUpdatedAt",
                "bookedAt", "fromCity", "toCity", "weightKg", "history");
        body.get("history").forEach(event ->
                assertThat(fieldNames(event)).containsExactlyInAnyOrder("statusCode", "changedAt"));
        assertThat(body.get("history").get(0).get("statusCode").asString()).isEqualTo("CREATED");
        assertThat(body.get("history").get(0).get("changedAt").asString()).isEqualTo("2026-11-01T04:00:00Z");
    }

    @Test
    void theJsonNeverContainsPersonalOrInternalData() {
        String body = json.writeValueAsString(PublicTrackingMapper.toResponse(parcel(), history()));

        assertThat(body).doesNotContain(
                // names, phone numbers, street addresses, postal codes
                "Ayesha", "Hassan", "Kamala", "+9477", "+9471", "Galle Road", "Peradeniya", "Flat 4B", "00300",
                "20000", "Western",
                // notes, instructions, contents, failure reasons, proof
                "Collected", "phoned", "Ring the bell", "Laptop", "not available", "proof", "https://",
                // ids of the parcel, its owner, the courier and the pricing rule
                PARCEL_ID.toString(), CUSTOMER_ID.toString(), MERCHANT_ID.toString(), COURIER_ID.toString(),
                PRICING_RULE_ID.toString(),
                // other internal or not-for-the-public fields
                "750", "LKR", "ELECTRONICS", "customerUserId", "merchantId", "changedByUserId", "parcelId",
                "\"id\"", "note", "failureReason", "recipientName", "proofFileUrl", "contactName", "contactPhone",
                "addressLine", "postalCode", "district", "zoneId", "quotedFee", "pricingRuleId");
    }
}
