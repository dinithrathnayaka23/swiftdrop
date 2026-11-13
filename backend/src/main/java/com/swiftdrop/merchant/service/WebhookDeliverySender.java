package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.model.MerchantWebhook;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Sends signed webhook requests and returns only delivery metadata. */
@Service
public class WebhookDeliverySender {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final int RESPONSE_EXCERPT_LENGTH = 1000;

    private final RestClient restClient;
    private final WebhookSecretService secretService;

    public WebhookDeliverySender(WebhookSecretService secretService) {
        this(secretService, createRestClient());
    }

    WebhookDeliverySender(WebhookSecretService secretService, RestClient restClient) {
        this.secretService = secretService;
        this.restClient = restClient;
    }

    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(REQUEST_TIMEOUT);
        requestFactory.setReadTimeout(REQUEST_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    public AttemptResult send(MerchantWebhook webhook, String payload) {
        Instant attemptedAt = Instant.now();
        String timestamp = Long.toString(attemptedAt.getEpochSecond());
        try {
            HttpResult result = restClient.post()
                    .uri(URI.create(webhook.getEndpointUrl()))
                    .header("X-SwiftDrop-Signature", signature(timestamp, payload,
                            secretService.decrypt(webhook.getSigningSecretEncrypted())))
                    .header("X-SwiftDrop-Timestamp", timestamp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((request, response) -> new HttpResult(response.getStatusCode().value(),
                            response.getBody() == null ? null
                                    : excerpt(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8))));
            return new AttemptResult(attemptedAt, result.status(), result.responseExcerpt(), null);
        } catch (RestClientException | IllegalArgumentException | IllegalStateException ex) {
            return new AttemptResult(attemptedAt, null, null,
                    "Webhook request failed: " + ex.getClass().getSimpleName());
        }
    }

    private static String signature(String timestamp, String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign webhook payload", ex);
        }
    }

    private static String excerpt(String response) {
        return response == null || response.length() <= RESPONSE_EXCERPT_LENGTH
                ? response : response.substring(0, RESPONSE_EXCERPT_LENGTH);
    }

    public record AttemptResult(Instant attemptedAt, Integer httpStatus, String responseExcerpt,
                                String errorMessage) {

        public boolean successful() {
            return httpStatus != null && httpStatus >= 200 && httpStatus < 300;
        }
    }

    private record HttpResult(int status, String responseExcerpt) {
    }
}
