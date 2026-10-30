package com.swiftdrop;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.common.messaging.RabbitConfig;
import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Boots the full application against real PostgreSQL and RabbitMQ containers
 * (requires Docker) and proves the database, migrations and broker work.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class InfrastructureIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.4-management-alpine");

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void flywayAppliedBaselineMigration() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        Integer roles = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class);
        assertThat(roles).isEqualTo(4);
    }

    @Test
    void rabbitTopologyIsDeclaredAndEventsRoundTrip() {
        assertThat(amqpAdmin.getQueueInfo(RabbitConfig.NOTIFICATION_QUEUE)).isNotNull();
        assertThat(amqpAdmin.getQueueInfo(RabbitConfig.WEBHOOK_QUEUE)).isNotNull();
        assertThat(amqpAdmin.getQueueInfo(RabbitConfig.DEAD_LETTER_QUEUE)).isNotNull();

        var event = new ParcelStatusChangedEvent(UUID.randomUUID(), "SD-TEST-0001", "CREATED", "ASSIGNED",
                Instant.now(), UUID.randomUUID());
        rabbitTemplate.convertAndSend(RabbitConfig.PARCEL_EVENTS_EXCHANGE, RabbitConfig.PARCEL_STATUS_CHANGED_KEY,
                event);

        ParcelStatusChangedEvent received = rabbitTemplate.receiveAndConvert(RabbitConfig.NOTIFICATION_QUEUE, 5_000,
                new ParameterizedTypeReference<>() { });
        assertThat(received).isEqualTo(event);
    }

    @Test
    void healthEndpointsReportUp() throws Exception {
        HttpResponse<String> ping = get("/api/v1/health");
        assertThat(ping.statusCode()).isEqualTo(200);

        HttpResponse<String> actuator = get("/actuator/health");
        assertThat(actuator.statusCode()).isEqualTo(200);
        assertThat(actuator.body()).contains("\"UP\"");
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
