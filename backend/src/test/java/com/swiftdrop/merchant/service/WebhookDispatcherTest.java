package com.swiftdrop.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.merchant.config.MerchantWebhookProperties;
import com.swiftdrop.merchant.config.WebhookRetryProperties;
import com.swiftdrop.merchant.model.MerchantWebhook;
import com.swiftdrop.merchant.model.WebhookDelivery;
import com.swiftdrop.merchant.model.WebhookDeliveryStatus;
import com.swiftdrop.merchant.repository.MerchantWebhookRepository;
import com.swiftdrop.merchant.repository.WebhookDeliveryRepository;
import com.swiftdrop.parcel.model.Parcel;
import com.swiftdrop.parcel.repository.ParcelRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class WebhookDispatcherTest {

    @Mock private MerchantWebhookRepository webhookRepository;
    @Mock private WebhookDeliveryRepository deliveryRepository;
    @Mock private ParcelRepository parcelRepository;
    @Mock private WebhookDeliverySender deliverySender;

    private WebhookDispatcher dispatcher;
    private MerchantWebhook webhook;
    private ParcelStatusChangedEvent event;
    private WebhookDelivery delivery;

    @BeforeEach
    void setUp() {
        dispatcher = new WebhookDispatcher(webhookRepository, deliveryRepository, parcelRepository,
                deliverySender, JsonMapper.builder().build(),
                new WebhookRetryProperties(List.of(Duration.ZERO, Duration.ZERO)));
        webhook = new MerchantWebhook();
        webhook.setId(UUID.randomUUID());
        webhook.setEndpointUrl("https://merchant.example/webhooks");
        webhook.setEventTypes(List.of("parcel.status.changed"));
        webhook.setActive(true);
        webhook.setSigningSecretEncrypted("encrypted");

        event = new ParcelStatusChangedEvent(UUID.randomUUID(), ParcelStatusChangedEvent.EVENT_TYPE,
                ParcelStatusChangedEvent.SCHEMA_VERSION, UUID.randomUUID(), "SD7KQ2MX9TRB4H",
                UUID.randomUUID(), "CREATED", "PICKED_UP", 2, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now());
        delivery = new WebhookDelivery();
        delivery.setId(UUID.randomUUID());
        delivery.setWebhook(webhook);
    }

    @Test
    void successfulStatusEventCreatesOneDeliveredDelivery() {
        stubEventRouting(false);
        Parcel parcel = new Parcel();
        parcel.setExternalOrderReference("order-123");
        when(parcelRepository.findWithDetailsById(event.parcelId())).thenReturn(Optional.of(parcel));
        when(deliverySender.send(any(MerchantWebhook.class), any(String.class)))
                .thenReturn(new WebhookDeliverySender.AttemptResult(Instant.now(), 200, "ok", null));

        dispatcher.onStatusChanged(event);

        WebhookDelivery saved = deliveryArgument();
        assertThat(saved.getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(saved.getLastHttpStatus()).isEqualTo(200);
        verify(deliveryRepository, org.mockito.Mockito.times(2)).save(saved);
    }

    @Test
    void duplicateStatusEventCreatesNoSecondDelivery() {
        stubEventRouting(true);

        dispatcher.onStatusChanged(event);

        verify(deliveryRepository, never()).save(any(WebhookDelivery.class));
        verify(parcelRepository, never()).findWithDetailsById(event.parcelId());
    }

    @Test
    void repeatedFailuresRemainPendingThenBecomeFailed() {
        stubEventRouting(false);
        Parcel parcel = new Parcel();
        when(parcelRepository.findWithDetailsById(event.parcelId())).thenReturn(Optional.of(parcel));
        when(deliverySender.send(any(MerchantWebhook.class), any(String.class)))
                .thenReturn(new WebhookDeliverySender.AttemptResult(Instant.now(), 503, "down", null));

        dispatcher.onStatusChanged(event);
        WebhookDelivery saved = deliveryArgument();
        assertThat(saved.getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(1);

        dispatcher.retry(saved);
        assertThat(saved.getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(2);

        dispatcher.retry(saved);
        assertThat(saved.getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(saved.getRetryCount()).isEqualTo(3);
        assertThat(saved.getLastHttpStatus()).isEqualTo(503);
        assertThat(saved.getLastErrorMessage()).contains("503");
    }

    @Test
    void hmacSignatureMatchesTimestampAndBodyAndChangesWithBody() {
        MerchantWebhookProperties properties = new MerchantWebhookProperties(
                "test-webhook-encryption-key-at-least-32-characters", false);
        WebhookSecretService secretService = new WebhookSecretService(properties);
        WebhookSecretService.GeneratedSecret generated = secretService.generate();
        MerchantWebhook signedWebhook = new MerchantWebhook();
        signedWebhook.setEndpointUrl("https://merchant.example/webhooks");
        signedWebhook.setSigningSecretEncrypted(generated.encryptedValue());

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WebhookDeliverySender sender = new WebhookDeliverySender(secretService, builder.build());
        AtomicReference<String> firstSignature = new AtomicReference<>();
        AtomicReference<String> secondSignature = new AtomicReference<>();
        String firstBody = "{\"type\":\"test\"}";
        String secondBody = "{\"type\":\"changed\"}";

        server.expect(requestTo(URI.create(signedWebhook.getEndpointUrl())))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    firstSignature.set(request.getHeaders().getFirst("X-SwiftDrop-Signature"));
                    assertThat(firstSignature.get()).isEqualTo(expectedSignature(request, firstBody,
                            generated.value()));
                })
                .andRespond(withSuccess());
        server.expect(requestTo(URI.create(signedWebhook.getEndpointUrl())))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> secondSignature.set(request.getHeaders().getFirst("X-SwiftDrop-Signature")))
                .andRespond(withSuccess());

        sender.send(signedWebhook, firstBody);
        sender.send(signedWebhook, secondBody);
        server.verify();

        assertThat(secondSignature.get()).isNotEqualTo(firstSignature.get());
    }

    private WebhookDelivery deliveryArgument() {
        org.mockito.ArgumentCaptor<WebhookDelivery> captor =
                org.mockito.ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues().get(0);
    }

    private void stubEventRouting(boolean duplicate) {
        when(webhookRepository.findFirstByMerchantIdAndActiveTrueOrderByCreatedAtAsc(event.merchantId()))
                .thenReturn(Optional.of(webhook));
        when(deliveryRepository.existsByWebhookIdAndStatusHistoryId(webhook.getId(), event.statusHistoryId()))
                .thenReturn(duplicate);
        if (!duplicate) {
            when(deliveryRepository.save(any(WebhookDelivery.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
        }
    }

    private static String expectedSignature(org.springframework.http.client.ClientHttpRequest request,
                                            String body, String secret) {
        try {
            String timestamp = request.getHeaders().getFirst("X-SwiftDrop-Timestamp");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
