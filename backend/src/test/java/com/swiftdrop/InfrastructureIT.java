package com.swiftdrop;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.common.messaging.RabbitConfig;
import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

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
    void healthEndpointsReportUp() {
        var ping = restTemplate.getForEntity("/api/v1/health", String.class);
        assertThat(ping.getStatusCode()).isEqualTo(HttpStatus.OK);

        var actuator = restTemplate.exchange("/actuator/health", HttpMethod.GET, null, String.class);
        assertThat(actuator.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(actuator.getBody()).contains("\"UP\"");
    }
}
