package com.swiftdrop;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Base for integration tests (*IT): boots the whole app on a random port against real
 * PostgreSQL, RabbitMQ and Redis containers (requires Docker). The containers are started once
 * and shared by every IT class in the run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final String SESSION_COOKIE = "swiftdrop_session";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.4-management-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.6-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        RABBIT.start();
        REDIS.start();
    }

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Marks an account's email as confirmed, as if the emailed link had been opened. For tests
     * about other features; the link itself is tested end to end in AuthFlowIT.
     */
    protected void verifyEmail(String email) {
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE email = lower(?)", email);
    }

    protected HttpResponse<String> get(String path, String bearerToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (bearerToken != null) request.header("Authorization", "Bearer " + bearerToken);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> get(String path) throws Exception {
        return get(path, null);
    }

    protected HttpResponse<String> post(String path, String jsonBody, String bearerToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (bearerToken != null) request.header("Authorization", "Bearer " + bearerToken);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> post(String path, String jsonBody) throws Exception {
        return post(path, jsonBody, null);
    }

    /** POST with the session cookie, as the browser sends it to /api/v1/auth/refresh and /logout. */
    protected HttpResponse<String> postWithSession(String path, String sessionCookieValue) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.noBody());
        if (sessionCookieValue != null) request.header("Cookie", SESSION_COOKIE + "=" + sessionCookieValue);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** The full Set-Cookie header for the session cookie, if the response set one. */
    protected static Optional<String> sessionSetCookie(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(header -> header.startsWith(SESSION_COOKIE + "="))
                .findFirst();
    }

    /** The session cookie value the response set (the refresh token). */
    protected static String sessionCookie(HttpResponse<?> response) {
        String header = sessionSetCookie(response).orElseThrow(() -> new AssertionError("No session cookie set"));
        return header.substring(SESSION_COOKIE.length() + 1, header.indexOf(';'));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
