package com.swiftdrop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.common.messaging.RabbitConfig;
import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the database, migrations, broker and health endpoints work against
 * real containers.
 */
class InfrastructureIT extends IntegrationTestBase {

        @Autowired
        private Flyway flyway;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Autowired
        private AmqpAdmin amqpAdmin;

        @Autowired
        private RabbitTemplate rabbitTemplate;

        @Test
        void databaseAllowsOnlyOneAdmin() {
                // The admin from application-test.yml already exists (created on startup).
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM users WHERE role = 'DISPATCHER_ADMIN'", Integer.class))
                                .isEqualTo(1);

                assertThatThrownBy(() -> jdbcTemplate.update(
                                "INSERT INTO users (full_name, email, password_hash, role, account_status) VALUES "
                                                + "('Second Admin', 'second-admin@test.swiftdrop.local', 'x', 'DISPATCHER_ADMIN', 'ACTIVE')"))
                                .isInstanceOf(DataIntegrityViolationException.class)
                                .hasMessageContaining("ux_users_single_admin");
        }

        @Test
        void flywayAppliedAllMigrations() {
                // V1 baseline (ER diagram) + V2 users.email_verified_at + V3 single admin, plus
                // the members'
                // own migrations (V<member>_<seq>); all applied, nothing failed or pending. No
                // exact latest
                // version, so a new migration from any member doesn't break this test.
                assertThat(flyway.info().applied()).extracting(migration -> migration.getVersion().getVersion())
                                .contains("1", "1.001", "2", "3");
                assertThat(flyway.info().applied())
                                .allMatch(migration -> migration.getState().isApplied()
                                                && !migration.getState().isFailed());
                assertThat(flyway.info().pending()).isEmpty();

                List<String> tables = jdbcTemplate.queryForList(
                                "SELECT table_name FROM information_schema.tables "
                                                + "WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'",
                                String.class);
                assertThat(tables).containsExactlyInAnyOrder(
                                "users", "couriers", "addresses", "parcel_statuses", "parcels", "parcel_status_history",
                                "delivery_assignments", "delivery_receipts", "notifications",
                                "notification_preferences", "merchants",
                                "merchant_api_credentials", "merchant_webhooks", "webhook_deliveries", "pricing_rules",
                                "zone_pricing_adjustments", "delivery_zones", "zone_coverage_areas",
                                // V1_001: Admin System Settings (one row).
                                "system_settings",
                                // V6_001: the delivery zones a courier covers.
                                "courier_service_areas");

                // The six report statuses (V4_001); ParcelServiceIT checks the codes and their
                // order.
                Integer statuses = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM parcel_statuses", Integer.class);
                assertThat(statuses).isEqualTo(6);
        }

        @Test
        void rabbitTopologyIsDeclaredAndEventsRoundTrip() {

                // Verify production queues are declared.
                assertThat(amqpAdmin.getQueueInfo(
                                RabbitConfig.NOTIFICATION_QUEUE)).isNotNull();

                assertThat(amqpAdmin.getQueueInfo(
                                RabbitConfig.WEBHOOK_QUEUE)).isNotNull();

                assertThat(amqpAdmin.getQueueInfo(
                                RabbitConfig.DEAD_LETTER_QUEUE)).isNotNull();

                // Create a unique queue for this test.
                String testQueueName = "swiftdrop.test.events." + UUID.randomUUID();

                Queue testQueue = new Queue(
                                testQueueName,
                                true, // durable
                                false, // not exclusive
                                false // do not auto-delete
                );

                amqpAdmin.declareQueue(testQueue);

                try {
                        // Bind test queue to the real parcel exchange.
                        amqpAdmin.declareBinding(
                                        BindingBuilder.bind(testQueue)
                                                        .to(new TopicExchange(
                                                                        RabbitConfig.PARCEL_EVENTS_EXCHANGE))
                                                        .with(RabbitConfig.PARCEL_STATUS_CHANGED_KEY));

                        // Construct a sample event.
                        var event = new ParcelStatusChangedEvent(
                                        UUID.randomUUID(),
                                        ParcelStatusChangedEvent.EVENT_TYPE,
                                        ParcelStatusChangedEvent.SCHEMA_VERSION,
                                        UUID.randomUUID(),
                                        "SD-TEST-0001",
                                        null,
                                        "CREATED",
                                        "PICKED_UP",
                                        2,
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        Instant.now());

                        // Publish through the real exchange.
                        rabbitTemplate.convertAndSend(
                                        RabbitConfig.PARCEL_EVENTS_EXCHANGE,
                                        RabbitConfig.PARCEL_STATUS_CHANGED_KEY,
                                        event);

                        // Receive from our dedicated test queue.
                        ParcelStatusChangedEvent received = rabbitTemplate.receiveAndConvert(
                                        testQueueName,
                                        10_000,
                                        new ParameterizedTypeReference<ParcelStatusChangedEvent>() {
                                        });

                        assertThat(received).isEqualTo(event);

                } finally {
                        // Always clean up the temporary queue.
                        amqpAdmin.deleteQueue(testQueueName);
                }
        }

        @Test
        void healthEndpointsReportUp() throws Exception {
                HttpResponse<String> ping = get("/api/v1/health");
                assertThat(ping.statusCode()).isEqualTo(200);

                HttpResponse<String> actuator = get("/actuator/health");
                assertThat(actuator.statusCode()).isEqualTo(200);
                assertThat(actuator.body()).contains("\"UP\"");
        }
}
