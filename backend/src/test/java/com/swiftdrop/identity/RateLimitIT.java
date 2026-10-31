package com.swiftdrop.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.notification.service.EmailService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Abuse protection end to end, against real Redis, with deliberately low limits. */
@TestPropertySource(properties = {
        "swiftdrop.rate-limit.login.requests=4",
        "swiftdrop.rate-limit.login.window=1m",
        "swiftdrop.rate-limit.failed-logins-per-account-and-ip.requests=2",
        "swiftdrop.rate-limit.failed-logins-per-account-and-ip.window=15m"})
class RateLimitIT extends IntegrationTestBase {

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JsonMapper json;

    @Value("${local.server.port}")
    private int port;

    @BeforeEach
    void startWithEmptyCounters() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private HttpResponse<String> login(String email, String password, String forwardedFor) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"email":"%s","password":"%s"}""".formatted(email, password)));
        if (forwardedFor != null) request.header("X-Forwarded-For", forwardedFor);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String registeredCustomer() throws Exception {
        String email = "limit-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        assertThat(post("/api/v1/auth/register/customer", """
                {"fullName":"Limit Tester","email":"%s","phone":"+94712345678","password":"parcel2026"}"""
                .formatted(email)).statusCode()).isEqualTo(201);
        verifyEmail(email);
        return email;
    }

    @Test
    void tooManySignInRequestsFromOneIpGet429WithRetryAfter() throws Exception {
        for (int i = 0; i < 4; i++) {
            assertThat(login("nobody-" + i + "@example.com", "whatever1", null).statusCode()).isEqualTo(401);
        }

        HttpResponse<String> blocked = login("nobody@example.com", "whatever1", null);

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValueAsLong("Retry-After").orElseThrow()).isBetween(1L, 60L);
        JsonNode body = json.readTree(blocked.body());
        assertThat(body.get("code").asString()).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(body.get("message").asString()).startsWith("Too many requests. Please try again in");
    }

    @Test
    void visitorsBehindTheProxyAreCountedSeparately() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("nobody@example.com", "whatever1", "198.51.100.1");
        }
        assertThat(login("nobody@example.com", "whatever1", "198.51.100.1").statusCode()).isEqualTo(429);

        // Another visitor (different X-Forwarded-For from the trusted proxy) is not affected.
        assertThat(login("nobody@example.com", "whatever1", "198.51.100.2").statusCode()).isEqualTo(401);
    }

    @Test
    void repeatedWrongPasswordsPauseThatAccountEvenForTheRightPassword() throws Exception {
        String email = registeredCustomer();
        String other = registeredCustomer();

        assertThat(login(email, "wrong-guess-1", null).statusCode()).isEqualTo(401);
        assertThat(login(email, "wrong-guess-2", null).statusCode()).isEqualTo(401);

        HttpResponse<String> paused = login(email, "parcel2026", null);
        assertThat(paused.statusCode()).isEqualTo(429);
        assertThat(json.readTree(paused.body()).get("message").asString())
                .startsWith("Too many failed sign-in attempts. Please try again in");

        // Other accounts can still sign in.
        assertThat(login(other, "parcel2026", null).statusCode()).isEqualTo(200);
    }

    @Test
    void aSuccessfulSignInClearsEarlierFailures() throws Exception {
        String email = registeredCustomer();

        assertThat(login(email, "wrong-guess-1", null).statusCode()).isEqualTo(401);
        assertThat(login(email, "parcel2026", null).statusCode()).isEqualTo(200);
        assertThat(login(email, "wrong-guess-2", null).statusCode()).isEqualTo(401);

        assertThat(login(email, "parcel2026", null).statusCode()).isEqualTo(200);
    }
}
