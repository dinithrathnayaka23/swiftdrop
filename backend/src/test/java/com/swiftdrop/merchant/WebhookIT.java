package com.swiftdrop.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import com.swiftdrop.merchant.service.WebhookRetryJob;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusChange;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelStatusService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Merchant webhooks end to end: a committed parcel status change goes through RabbitMQ to the
 * merchant's HTTPS endpoint as a signed request, and every attempt is logged, retried with
 * backoff, and finally marked failed (and can then be retried by the merchant).
 *
 * <p>The endpoint is a local HTTPS server with a certificate made for this test; the JVM trusts it
 * while these tests run.
 */
class WebhookIT extends MerchantIntegrationTestBase {

    private static final String WEBHOOK = "/api/v1/merchant/webhook";
    private static final String DELIVERIES = WEBHOOK + "/deliveries";
    private static final String STORE_PASSWORD = "webhook-it";

    private static Receiver receiver;
    private static SSLSocketFactory previousSocketFactory;

    @Autowired private ParcelStatusService statusService;
    @Autowired private WebhookRetryJob retryJob;

    private SignedInMerchant merchant;
    private String endpointPath;

    /** A request the merchant's endpoint received. */
    private record Received(String timestamp, String signature, String body) {
    }

    /** The merchant's endpoint: records each request and answers with the planned statuses (200 by default). */
    private static final class Receiver {

        private final HttpsServer server;
        private final Map<String, BlockingQueue<Received>> received = new ConcurrentHashMap<>();
        private final Map<String, Deque<Integer>> plannedStatuses = new ConcurrentHashMap<>();

        Receiver(SSLContext ssl) throws IOException {
            server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(ssl));
            server.createContext("/", this::handle);
            server.start();
        }

        String url(String path) {
            return "https://localhost:" + server.getAddress().getPort() + path;
        }

        /** The next responses for this path, in order; afterwards 200. */
        void answer(String path, Integer... statuses) {
            plannedStatuses.computeIfAbsent(path, key -> new ConcurrentLinkedDeque<>()).addAll(List.of(statuses));
        }

        Received next(String path) throws InterruptedException {
            Received request = queue(path).poll(15, TimeUnit.SECONDS);
            assertThat(request).as("a webhook request to " + path).isNotNull();
            return request;
        }

        int count(String path) {
            return queue(path).size();
        }

        void stop() {
            server.stop(0);
        }

        private BlockingQueue<Received> queue(String path) {
            return received.computeIfAbsent(path, key -> new LinkedBlockingQueue<>());
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String body;
            try (InputStream in = exchange.getRequestBody()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            queue(path).add(new Received(exchange.getRequestHeaders().getFirst("X-SwiftDrop-Timestamp"),
                    exchange.getRequestHeaders().getFirst("X-SwiftDrop-Signature"), body));
            Integer planned = plannedStatuses.getOrDefault(path, new ConcurrentLinkedDeque<>()).poll();
            int status = planned == null ? 200 : planned;
            byte[] response = (status < 300 ? "ok" : "boom " + status).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        }
    }

    @BeforeAll
    static void startReceiver() throws Exception {
        SSLContext ssl = selfSignedLocalhost();
        previousSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
        HttpsURLConnection.setDefaultSSLSocketFactory(ssl.getSocketFactory());
        receiver = new Receiver(ssl);
    }

    @AfterAll
    static void stopReceiver() {
        receiver.stop();
        HttpsURLConnection.setDefaultSSLSocketFactory(previousSocketFactory);
    }

    /** A certificate for localhost, made with the JDK's keytool, used by the server and trusted by the client. */
    private static SSLContext selfSignedLocalhost() throws Exception {
        Path store = Files.createTempDirectory("webhook-it").resolve("localhost.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "localhost", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "2", "-dname", "CN=localhost",
                "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-storetype", "PKCS12", "-keystore", store.toString(),
                "-storepass", STORE_PASSWORD, "-keypass", STORE_PASSWORD)
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(store)) {
            keyStore.load(in, STORE_PASSWORD.toCharArray());
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(keyStore, STORE_PASSWORD.toCharArray());
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(keyStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
        return context;
    }

    @BeforeEach
    void signIn() {
        merchant = approvedMerchant("BrightMart");
        endpointPath = "/hooks/" + UUID.randomUUID();
    }

    /** Saves the webhook settings and returns the response (with the signing secret on creation). */
    private JsonNode configureWebhook() throws Exception {
        return ok(put(WEBHOOK, """
                {"endpointUrl":"%s","eventTypes":["PARCEL_STATUS_CHANGED"],"active":true}"""
                .formatted(receiver.url(endpointPath)), merchant.token()), 200);
    }

    private ParcelDetails pickedUpOrder(String reference) {
        ParcelDetails parcel = order(merchant.merchantId(), reference, "Nimal Perera", "Kandy");
        statusService.changeStatus(parcel.id(), StatusChange.to(ParcelStatusCode.PICKED_UP, null, null));
        return parcel;
    }

    /** The merchant's only delivery, once it matches {@code status}. */
    private JsonNode delivery(String status) throws Exception {
        return eventually(() -> ok(get(DELIVERIES, merchant.token()), 200),
                page -> page.get("content").size() == 1
                        && page.get("content").get(0).get("status").asString().equals(status))
                .get("content").get(0);
    }

    private static String sign(String secret, String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    private static void assertSignedWith(String secret, Received request) throws Exception {
        assertThat(request.signature()).isEqualTo(sign(secret, request.timestamp(), request.body()));
        assertThat(Instant.ofEpochSecond(Long.parseLong(request.timestamp())))
                .isBetween(Instant.now().minus(Duration.ofMinutes(1)), Instant.now().plusSeconds(1));
    }

    @Test
    void aStatusChangeSendsASignedWebhookAndLogsTheDelivery() throws Exception {
        JsonNode settings = configureWebhook();
        String secret = settings.get("signingSecret").asString();
        assertThat(secret).isNotBlank();
        JsonNode reloaded = ok(get(WEBHOOK, merchant.token()), 200);
        assertThat(reloaded.path("signingSecret").isMissingNode() || reloaded.get("signingSecret").isNull())
                .as("the secret is shown only once").isTrue();

        ParcelDetails parcel = pickedUpOrder("ORD-5001");

        Received request = receiver.next(endpointPath);
        assertSignedWith(secret, request);
        JsonNode payload = json.readTree(request.body());
        assertThat(payload.get("type").asString()).isEqualTo("PARCEL_STATUS_CHANGED");
        assertThat(payload.get("trackingNumber").asString()).isEqualTo(parcel.trackingNumber());
        assertThat(payload.get("externalOrderReference").asString()).isEqualTo("ORD-5001");
        assertThat(payload.get("previousStatus").asString()).isEqualTo("CREATED");
        assertThat(payload.get("newStatus").asString()).isEqualTo("PICKED_UP");
        assertThat(payload.get("eventId").asString()).isNotBlank();
        // No personal data is sent.
        assertThat(request.body()).doesNotContain("Nimal").doesNotContain("+9477").doesNotContain("Temple");

        JsonNode delivery = delivery("DELIVERED");
        assertThat(delivery.get("parcelId").asString()).isEqualTo(parcel.id().toString());
        assertThat(delivery.get("eventType").asString()).isEqualTo("PARCEL_STATUS_CHANGED");
        assertThat(delivery.get("retryCount").asInt()).isZero();
        assertThat(delivery.get("lastHttpStatus").asInt()).isEqualTo(200);
        assertThat(delivery.get("deliveredAt").isNull()).isFalse();
    }

    @Test
    void aFailedDeliveryIsRetriedWithBackoffUntilItIsDelivered() throws Exception {
        String secret = configureWebhook().get("signingSecret").asString();
        receiver.answer(endpointPath, 500);

        pickedUpOrder("ORD-5002");

        Received first = receiver.next(endpointPath);
        JsonNode failed = delivery("PENDING");
        assertThat(failed.get("retryCount").asInt()).isEqualTo(1);
        assertThat(failed.get("lastHttpStatus").asInt()).isEqualTo(500);
        assertThat(failed.get("lastResponseExcerpt").asString()).isEqualTo("boom 500");
        assertThat(failed.get("lastErrorMessage").asString()).isEqualTo("Webhook endpoint returned HTTP 500");
        // The first backoff step is one minute.
        assertThat(Duration.between(Instant.parse(failed.get("lastAttemptAt").asString()),
                Instant.parse(failed.get("nextRetryAt").asString()))).isEqualTo(Duration.ofMinutes(1));

        makeDue(failed);
        retryJob.retryDueDeliveries();

        Received retried = receiver.next(endpointPath);
        // The same event again (re-read from the database, so the key order may differ), signed anew.
        assertThat(json.readTree(retried.body())).isEqualTo(json.readTree(first.body()));
        assertSignedWith(secret, retried);
        JsonNode delivered = delivery("DELIVERED");
        assertThat(delivered.get("retryCount").asInt()).isEqualTo(1);
        assertThat(delivered.get("nextRetryAt").isNull()).isTrue();
        assertThat(delivered.get("lastErrorMessage").isNull()).isTrue();
    }

    @Test
    void afterTheLastRetryTheDeliveryFailsAndTheMerchantCanRetryIt() throws Exception {
        configureWebhook();
        receiver.answer(endpointPath, 503, 503);

        pickedUpOrder("ORD-5003");
        receiver.next(endpointPath);
        JsonNode pending = delivery("PENDING");

        // Skip ahead to the last of the five backoff steps.
        jdbc.update("UPDATE webhook_deliveries SET retry_count = 5 WHERE webhook_delivery_id = ?::uuid",
                pending.get("deliveryId").asString());
        makeDue(pending);
        retryJob.retryDueDeliveries();
        receiver.next(endpointPath);

        JsonNode failed = delivery("FAILED");
        assertThat(failed.get("retryCount").asInt()).isEqualTo(6);
        assertThat(failed.get("nextRetryAt").isNull()).isTrue();
        String deliveryId = failed.get("deliveryId").asString();
        assertThat(ok(get(DELIVERIES + "?status=FAILED", merchant.token()), 200).get("content").size())
                .isEqualTo(1);

        // Another merchant can't retry it.
        assertError(post(DELIVERIES + "/" + deliveryId + "/retry", "", approvedMerchant("Other Shop").token()),
                404, "NOT_FOUND");

        JsonNode retried = ok(post(DELIVERIES + "/" + deliveryId + "/retry", "", merchant.token()), 200);
        assertThat(retried.get("status").asString()).isEqualTo("DELIVERED");
        receiver.next(endpointPath);
        assertError(post(DELIVERIES + "/" + deliveryId + "/retry", "", merchant.token()), 409, "CONFLICT");
    }

    @Test
    void theMerchantCanSendATestEventAndRotateTheSecret() throws Exception {
        String oldSecret = configureWebhook().get("signingSecret").asString();

        JsonNode result = ok(post(WEBHOOK + "/test", "", merchant.token()), 200);

        assertThat(result.get("delivered").asBoolean()).isTrue();
        assertThat(result.get("httpStatus").asInt()).isEqualTo(200);
        Received test = receiver.next(endpointPath);
        assertSignedWith(oldSecret, test);
        assertThat(json.readTree(test.body()).get("type").asString()).isEqualTo("test");

        String newSecret = ok(post(WEBHOOK + "/rotate-secret", "", merchant.token()), 200)
                .get("signingSecret").asString();
        assertThat(newSecret).isNotEqualTo(oldSecret);
        ok(post(WEBHOOK + "/test", "", merchant.token()), 200);
        Received afterRotation = receiver.next(endpointPath);
        assertSignedWith(newSecret, afterRotation);
        assertThat(afterRotation.signature())
                .isNotEqualTo(sign(oldSecret, afterRotation.timestamp(), afterRotation.body()));
        assertThat(receiver.count(endpointPath)).isZero();
    }

    @Test
    void anEndpointMustUseHttpsWithoutCredentials() throws Exception {
        for (String url : List.of("http://localhost:9/hooks", "https://user:pass@localhost/hooks", "not a url")) {
            assertThat(put(WEBHOOK, """
                    {"endpointUrl":"%s","eventTypes":["PARCEL_STATUS_CHANGED"],"active":true}"""
                    .formatted(url), merchant.token()).statusCode()).as(url).isEqualTo(400);
        }
        assertError(get(WEBHOOK, merchant.token()), 404, "NOT_FOUND");
    }

    private void makeDue(JsonNode delivery) {
        jdbc.update("UPDATE webhook_deliveries SET next_retry_at = now() - interval '1 second' "
                + "WHERE webhook_delivery_id = ?::uuid", delivery.get("deliveryId").asString());
    }
}
