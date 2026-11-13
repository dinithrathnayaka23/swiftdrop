package com.swiftdrop.merchant.service;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.merchant.config.MerchantWebhookProperties;
import com.swiftdrop.merchant.dto.request.UpdateWebhookSettingsRequest;
import com.swiftdrop.merchant.dto.response.WebhookSettingsResponse;
import com.swiftdrop.merchant.model.Merchant;
import com.swiftdrop.merchant.model.MerchantWebhook;
import com.swiftdrop.merchant.repository.MerchantWebhookRepository;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WebhookSettingsService {

    private final MerchantWebhookRepository webhookRepository;
    private final WebhookSecretService secretService;
    private final MerchantWebhookProperties properties;

    @Transactional(readOnly = true)
    public WebhookSettingsResponse get(UUID merchantId) {
        return webhookRepository.findFirstByMerchantIdOrderByCreatedAtAsc(merchantId)
                .map(webhook -> toResponse(webhook, null))
                .orElseThrow(() -> new NotFoundException("Webhook settings have not been configured"));
    }

    @Transactional
    public WebhookSettingsResponse update(UUID merchantId, UpdateWebhookSettingsRequest request) {
        validateEndpoint(request.endpointUrl());
        MerchantWebhook webhook = webhookRepository.findFirstByMerchantIdOrderByCreatedAtAsc(merchantId)
                .orElseGet(() -> {
                    MerchantWebhook created = new MerchantWebhook();
                    Merchant merchant = new Merchant();
                    merchant.setId(merchantId);
                    created.setMerchant(merchant);
                    return created;
                });
        boolean created = webhook.getId() == null;
        if (created) {
            WebhookSecretService.GeneratedSecret secret = secretService.generate();
            webhook.setSigningSecretEncrypted(secret.encryptedValue());
            webhook.setEndpointUrl(request.endpointUrl());
            webhook.setEventTypes(List.copyOf(request.eventTypes()));
            webhook.setActive(request.active());
            MerchantWebhook saved = webhookRepository.save(webhook);
            return toResponse(saved, secret.value());
        }
        webhook.setEndpointUrl(request.endpointUrl());
        webhook.setEventTypes(List.copyOf(request.eventTypes()));
        webhook.setActive(request.active());
        return toResponse(webhookRepository.save(webhook), null);
    }

    @Transactional
    public WebhookSettingsResponse rotateSecret(UUID merchantId) {
        MerchantWebhook webhook = webhookRepository.findFirstByMerchantIdOrderByCreatedAtAsc(merchantId)
                .orElseThrow(() -> new NotFoundException("Webhook settings have not been configured"));
        WebhookSecretService.GeneratedSecret secret = secretService.generate();
        webhook.setSigningSecretEncrypted(secret.encryptedValue());
        return toResponse(webhookRepository.save(webhook), secret.value());
    }

    private void validateEndpoint(String endpointUrl) {
        URI uri;
        try {
            uri = URI.create(endpointUrl);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Webhook endpoint URL is invalid");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BadRequestException("Webhook endpoint URL must use HTTPS and include a valid host");
        }
        if (!properties.allowPrivateHosts() && isPrivateHost(uri.getHost())) {
            throw new BadRequestException("Webhook endpoint host must be public");
        }
    }

    private boolean isPrivateHost(String host) {
        if ("localhost".equalsIgnoreCase(host) || host.endsWith(".localhost")) {
            return true;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                byte[] bytes = address.getAddress();
                boolean uniqueLocalIpv6 = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress() || uniqueLocalIpv6) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException ex) {
            throw new BadRequestException("Webhook endpoint host cannot be resolved");
        }
    }

    private WebhookSettingsResponse toResponse(MerchantWebhook webhook, String signingSecret) {
        return new WebhookSettingsResponse(webhook.getId(), webhook.getEndpointUrl(),
                List.copyOf(webhook.getEventTypes()), webhook.isActive(), signingSecret, webhook.getCreatedAt());
    }
}
