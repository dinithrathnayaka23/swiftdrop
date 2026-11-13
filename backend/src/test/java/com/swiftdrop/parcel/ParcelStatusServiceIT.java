package com.swiftdrop.parcel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.messaging.RabbitConfig;
import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.parcel.dto.NewAddress;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusChange;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;

/** Status changes against the real database and broker. */
class ParcelStatusServiceIT extends IntegrationTestBase {

    @Autowired private ParcelService parcelService;
    @Autowired private ParcelStatusService statusService;
    @Autowired private UserService userService;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private AmqpAdmin amqpAdmin;

    private UUID customerId;
    private UUID courierId;

    /**
     * Empty both queues to keep other tests' events out. The events are read from the notification
     * queue, which has no consumer yet; WebhookDispatcher consumes the webhook queue.
     */
    @BeforeEach
    @AfterEach
    void emptyQueues() {
        amqpAdmin.purgeQueue(RabbitConfig.NOTIFICATION_QUEUE);
        amqpAdmin.purgeQueue(RabbitConfig.WEBHOOK_QUEUE);
    }

    @BeforeEach
    void createUsers() {
        customerId = newUser(Role.CUSTOMER);
        courierId = newUser(Role.COURIER);
    }

    private UUID newUser(Role role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.swiftdrop.local";
        return userService.createUser(NewUser.withPassword("Test User", email, "+94771234567", "x", role,
                AccountStatus.ACTIVE)).id();
    }

    private ParcelDetails newParcel() {
        NewAddress address = new NewAddress("Kamala Silva", "+94771234567", "12 Galle Road", null, "Colombo",
                "Colombo", null, null);
        return parcelService.createParcel(new NewParcel(customerId, null, null, address, address, ParcelType.PARCEL,
                BigDecimal.ONE, null, null, null, null, null, null, new BigDecimal("450.00")));
    }

    private ParcelStatusChangedEvent nextStatusEvent() {
        ParcelStatusChangedEvent event = rabbitTemplate.receiveAndConvert(RabbitConfig.NOTIFICATION_QUEUE, 5_000,
                new ParameterizedTypeReference<>() { });
        assertThat(event).as("event on " + RabbitConfig.NOTIFICATION_QUEUE).isNotNull();
        return event;
    }

    @Test
    void parcelGoesThroughTheWholeLifecycleWithAHistoryEntryAndEventForEachStep() {
        ParcelDetails parcel = newParcel();

        statusService.changeStatus(parcel.id(), StatusChange.to(ParcelStatusCode.PICKED_UP, courierId, null));
        statusService.changeStatus(parcel.id(), StatusChange.to(ParcelStatusCode.IN_TRANSIT, courierId, null));
        statusService.changeStatus(parcel.id(), StatusChange.to(ParcelStatusCode.OUT_FOR_DELIVERY, courierId, null));
        StatusHistoryEntry delivered = statusService.changeStatus(parcel.id(),
                StatusChange.delivered(courierId, "Kamala Silva", null, "Handed over at the door"));

        assertThat(parcelService.getParcel(parcel.id()).currentStatus()).isEqualTo(ParcelStatusCode.DELIVERED);

        List<StatusHistoryEntry> history = statusService.getHistory(parcel.id());
        assertThat(history).extracting(StatusHistoryEntry::status).containsExactly(ParcelStatusCode.CREATED,
                ParcelStatusCode.PICKED_UP, ParcelStatusCode.IN_TRANSIT, ParcelStatusCode.OUT_FOR_DELIVERY,
                ParcelStatusCode.DELIVERED);
        assertThat(history.getLast().recipientName()).isEqualTo("Kamala Silva");
        assertThat(history.getLast().changedByUserId()).isEqualTo(courierId);

        List<ParcelStatusChangedEvent> events = List.of(nextStatusEvent(), nextStatusEvent(), nextStatusEvent(),
                nextStatusEvent());
        assertThat(events).extracting(ParcelStatusChangedEvent::newStatus)
                .containsExactly("PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED");
        assertThat(events).extracting(ParcelStatusChangedEvent::statusVersion).containsExactly(2L, 3L, 4L, 5L);
        assertThat(events.getLast().statusHistoryId()).isEqualTo(delivered.id());
        assertThat(events.getLast().trackingNumber()).isEqualTo(parcel.trackingNumber());
        assertThat(events).allMatch(event -> event.parcelId().equals(parcel.id()));
    }

    @Test
    void rejectedChangeSavesAndPublishesNothing() {
        ParcelDetails parcel = newParcel();

        assertThatThrownBy(() -> statusService.changeStatus(parcel.id(),
                StatusChange.to(ParcelStatusCode.IN_TRANSIT, courierId, null)))
                .isInstanceOf(ConflictException.class);

        assertThat(parcelService.getParcel(parcel.id()).currentStatus()).isEqualTo(ParcelStatusCode.CREATED);
        assertThat(statusService.getHistory(parcel.id())).hasSize(1);
        assertThat(rabbitTemplate.receive(RabbitConfig.NOTIFICATION_QUEUE, 500)).isNull();
    }

    @Test
    void aFailedAttemptIsRetriedThenClosedAsAnEndState() {
        ParcelDetails parcel = newParcel();
        statusService.changeStatus(parcel.id(), StatusChange.to(ParcelStatusCode.PICKED_UP, courierId, null));

        // The old direct arrow is gone: nothing saved, nothing published.
        assertThatThrownBy(() -> statusService.changeStatus(parcel.id(),
                StatusChange.failed(courierId, "Parcel damaged", null)))
                .isInstanceOf(ConflictException.class);

        // The courier reports a failed attempt (reason kept).
        statusService.changeStatus(parcel.id(), StatusChange.failedAttempt(courierId, "Recipient not at home", null));
        assertThat(parcelService.getParcel(parcel.id()).currentStatus()).isEqualTo(ParcelStatusCode.FAILED_ATTEMPT);
        assertThat(statusService.getHistory(parcel.id()).getLast().failureReason()).isEqualTo("Recipient not at home");

        // The dispatcher retries: back to CREATED with a history row and an event.
        StatusHistoryEntry retried = statusService.changeStatus(parcel.id(),
                StatusChange.to(ParcelStatusCode.CREATED, courierId, "Second attempt tomorrow"));
        assertThat(retried.previousStatus()).isEqualTo(ParcelStatusCode.FAILED_ATTEMPT);
        assertThat(retried.failureReason()).isNull();

        // Second attempt fails too and the dispatcher closes it.
        statusService.changeStatus(parcel.id(), StatusChange.to(ParcelStatusCode.PICKED_UP, courierId, null));
        statusService.changeStatus(parcel.id(), StatusChange.failedAttempt(courierId, "Address not found", null));
        statusService.changeStatus(parcel.id(), StatusChange.failed(courierId, "Address does not exist", null));

        List<StatusHistoryEntry> history = statusService.getHistory(parcel.id());
        assertThat(history).extracting(StatusHistoryEntry::status).containsExactly(ParcelStatusCode.CREATED,
                ParcelStatusCode.PICKED_UP, ParcelStatusCode.FAILED_ATTEMPT, ParcelStatusCode.CREATED,
                ParcelStatusCode.PICKED_UP, ParcelStatusCode.FAILED_ATTEMPT, ParcelStatusCode.FAILED);
        assertThat(history.getLast().failureReason()).isEqualTo("Address does not exist");

        List<ParcelStatusChangedEvent> events = List.of(nextStatusEvent(), nextStatusEvent(), nextStatusEvent(),
                nextStatusEvent(), nextStatusEvent(), nextStatusEvent());
        assertThat(events).extracting(ParcelStatusChangedEvent::newStatus).containsExactly("PICKED_UP",
                "FAILED_ATTEMPT", "CREATED", "PICKED_UP", "FAILED_ATTEMPT", "FAILED");
        assertThat(events.get(2).previousStatus()).isEqualTo("FAILED_ATTEMPT");
        assertThat(events.get(2).statusHistoryId()).isEqualTo(retried.id());
        assertThat(events).extracting(ParcelStatusChangedEvent::statusVersion)
                .containsExactly(2L, 3L, 4L, 5L, 6L, 7L);

        // FAILED is an end state.
        assertThatThrownBy(() -> statusService.changeStatus(parcel.id(),
                StatusChange.to(ParcelStatusCode.CREATED, courierId, null)))
                .isInstanceOf(ConflictException.class);
    }
}
