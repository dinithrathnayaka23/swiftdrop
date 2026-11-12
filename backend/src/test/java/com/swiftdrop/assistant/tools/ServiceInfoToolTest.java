package com.swiftdrop.assistant.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.settings.dto.SystemSettingsView;
import com.swiftdrop.settings.service.SystemSettingsService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ServiceInfoToolTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void givesContactDetailsLimitsAndEveryParcelTypeButNotTheCompanyAddress() {
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.current()).thenReturn(new SystemSettingsView("SwiftDrop", "help@swiftdrop.lk",
                "+94112345678", "1 Main Street, Colombo", "Asia/Colombo", null, null, 10, "PARCEL",
                new BigDecimal("30.00"), new BigDecimal("150"), null, false, false, false, false, false, false, 1,
                null));
        var tool = new ServiceInfoTool(settingsService, JSON);

        JsonNode result = new ToolRegistry(List.of(tool), JSON)
                .run(ServiceInfoTool.NAME, JSON.createObjectNode(), new ToolContext(UUID.randomUUID(), "1.2.3.4"))
                .result();

        assertThat(result.get("contactEmail").asString()).isEqualTo("help@swiftdrop.lk");
        assertThat(result.get("parcelTypes")).hasSize(ParcelType.values().length);
        assertThat(result.get("message").asString()).contains("Maximum weight 30 kg", "+94112345678");
        assertThat(result.toString()).doesNotContain("Main Street");
    }
}
