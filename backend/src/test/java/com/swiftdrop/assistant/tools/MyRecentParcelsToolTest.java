package com.swiftdrop.assistant.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swiftdrop.parcel.dto.AddressDetails;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.json.JsonMapper;

class MyRecentParcelsToolTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ParcelService parcelService = mock(ParcelService.class);
    private final ToolRegistry registry = new ToolRegistry(List.of(new MyRecentParcelsTool(parcelService, JSON)), JSON);

    private static ParcelDetails parcel(UUID customerId) {
        var from = new AddressDetails(UUID.randomUUID(), "Ayesha Khan", "+94771234567", "12 Galle Road", null,
                "Colombo", "Western", "00300", null);
        var to = new AddressDetails(UUID.randomUUID(), "Hassan Ali", "+94719876543", "45 Peradeniya Road", null,
                "Kandy", "Central", "20000", null);
        return new ParcelDetails(UUID.randomUUID(), "SD7KQ2MX9TRB4H", customerId, null, null, from, to,
                ParcelType.ELECTRONICS, new BigDecimal("2.5"), null, null, null, "Laptop", "Ring twice",
                ParcelStatusCode.IN_TRANSIT, null, new BigDecimal("750.00"), "LKR", Instant.now(), Instant.now());
    }

    @Test
    void listsTheSignedInCustomersParcelsAndIgnoresAnyCustomerIdFromTheModel() {
        UUID customerId = UUID.randomUUID();
        Page<ParcelDetails> page = new PageImpl<>(List.of(parcel(customerId)));
        when(parcelService.listByCustomer(eq(customerId), any(Pageable.class))).thenReturn(page);

        ToolOutput output = registry.run(MyRecentParcelsTool.NAME,
                JSON.createObjectNode().put("customerId", UUID.randomUUID().toString()),
                new ToolContext(customerId, "1.2.3.4"));
        assertThat(output.result().get("error").asString()).isEqualTo("INVALID_INPUT");

        output = registry.run(MyRecentParcelsTool.NAME, JSON.createObjectNode(), new ToolContext(customerId, "1.2.3.4"));

        assertThat(output.result().get("message").asString()).isEqualTo("Your recent parcels: SD7KQ2MX9TRB4H (In transit).");
        assertThat(output.result().toString()).doesNotContain("Colombo", "Kandy", "Ayesha", "Galle", "+9477",
                "Laptop", "750", customerId.toString());
        assertThat(output.card().parcels()).singleElement().satisfies(summary -> {
            assertThat(summary.fromCity()).isEqualTo("Colombo");
            assertThat(summary.toCity()).isEqualTo("Kandy");
        });
    }

    @Test
    void saysSoWhenThereAreNoParcels() {
        when(parcelService.listByCustomer(any(), any(Pageable.class))).thenReturn(Page.empty());

        ToolOutput output = registry.run(MyRecentParcelsTool.NAME, JSON.createObjectNode(),
                new ToolContext(UUID.randomUUID(), "1.2.3.4"));

        assertThat(output.result().get("message").asString()).isEqualTo(MyRecentParcelsTool.NONE);
        assertThat(output.card()).isNull();
    }
}
