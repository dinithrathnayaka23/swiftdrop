package com.swiftdrop.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.merchant.config.MerchantWebhookProperties;
import com.swiftdrop.merchant.dto.request.UpdateWebhookSettingsRequest;
import com.swiftdrop.merchant.model.MerchantWebhook;
import com.swiftdrop.merchant.repository.MerchantWebhookRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebhookSettingsServiceTest {

    @Mock private MerchantWebhookRepository webhookRepository;

    private WebhookSettingsService service;
    private final UUID merchantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MerchantWebhookProperties properties = new MerchantWebhookProperties(
                "test-webhook-encryption-key-at-least-32-characters", false);
        service = new WebhookSettingsService(webhookRepository, new WebhookSecretService(properties), properties);
    }

    @Test
    void createsSettingsAndReturnsSecretOnlyOnCreation() {
        when(webhookRepository.findFirstByMerchantIdOrderByCreatedAtAsc(merchantId)).thenReturn(Optional.empty());
        when(webhookRepository.save(any(MerchantWebhook.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.update(merchantId,
                new UpdateWebhookSettingsRequest("https://1.1.1.1/webhooks", List.of("parcel.status.changed"),
                        true));

        assertThat(response.signingSecret()).isNotBlank();
        assertThat(response.signingSecret()).hasSizeGreaterThan(32);
        verify(webhookRepository).save(any(MerchantWebhook.class));
    }

    @Test
    void doesNotReturnSecretWhenReadingSettings() {
        MerchantWebhook webhook = new MerchantWebhook();
        webhook.setId(UUID.randomUUID());
        webhook.setEndpointUrl("https://1.1.1.1/webhooks");
        webhook.setEventTypes(List.of("parcel.status.changed"));
        webhook.setSigningSecretEncrypted("encrypted");
        when(webhookRepository.findFirstByMerchantIdOrderByCreatedAtAsc(merchantId))
                .thenReturn(Optional.of(webhook));

        assertThat(service.get(merchantId).signingSecret()).isNull();
    }

    @Test
    void rejectsPrivateHostWhenPrivateHostsAreDisabled() {
        assertThatThrownBy(() -> service.update(merchantId,
                new UpdateWebhookSettingsRequest("https://localhost/webhooks", List.of("parcel.status.changed"),
                        true))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rotationRequiresExistingSettings() {
        when(webhookRepository.findFirstByMerchantIdOrderByCreatedAtAsc(merchantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotateSecret(merchantId)).isInstanceOf(NotFoundException.class);
    }
}
