package com.swiftdrop.realtime.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.realtime.dto.LiveStatusUpdate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ParcelStatusEventListenerTest {

    private static final Instant CHANGED_AT = Instant.parse("2026-11-07T04:30:00Z");

    @Mock private ApplicationEventPublisher eventPublisher;

    private ParcelStatusEventListener listener;
    private final UUID parcelId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new ParcelStatusEventListener(new RecentStatusEvents(), eventPublisher);
    }

    private ParcelStatusChangedEvent event(UUID eventId, UUID parcel, String from, String to, long version) {
        return new ParcelStatusChangedEvent(eventId, ParcelStatusChangedEvent.EVENT_TYPE,
                ParcelStatusChangedEvent.SCHEMA_VERSION, parcel, "SD12345678", UUID.randomUUID(), from, to,
                version, UUID.randomUUID(), UUID.randomUUID(), CHANGED_AT);
    }

    private ParcelStatusChangedEvent event(String from, String to, long version) {
        return event(UUID.randomUUID(), parcelId, from, to, version);
    }

    @Test
    void announcesTheUpdateWithOnlyPublicFields() {
        listener.onStatusChanged(event("CREATED", "PICKED_UP", 2));

        verify(eventPublisher).publishEvent(
                new LiveStatusUpdate("SD12345678", "CREATED", "PICKED_UP", 2, CHANGED_AT));
    }

    @Test
    void skipsARedeliveredEvent() {
        ParcelStatusChangedEvent pickedUp = event("CREATED", "PICKED_UP", 2);

        listener.onStatusChanged(pickedUp);
        listener.onStatusChanged(pickedUp);

        verify(eventPublisher).publishEvent(any(LiveStatusUpdate.class));
    }

    @Test
    void skipsAnOlderEventThatArrivesAfterANewerOne() {
        List<Object> published = new ArrayList<>();
        listener = new ParcelStatusEventListener(new RecentStatusEvents(), published::add);

        listener.onStatusChanged(event("PICKED_UP", "IN_TRANSIT", 3));
        listener.onStatusChanged(event("CREATED", "PICKED_UP", 2));
        listener.onStatusChanged(event("IN_TRANSIT", "OUT_FOR_DELIVERY", 4));

        assertThat(published).extracting(update -> ((LiveStatusUpdate) update).status())
                .containsExactly("IN_TRANSIT", "OUT_FOR_DELIVERY");
    }

    @Test
    void tracksVersionsPerParcel() {
        listener.onStatusChanged(event(UUID.randomUUID(), parcelId, "PICKED_UP", "IN_TRANSIT", 3));
        listener.onStatusChanged(event(UUID.randomUUID(), UUID.randomUUID(), "CREATED", "PICKED_UP", 2));

        verify(eventPublisher, times(2)).publishEvent(any(LiveStatusUpdate.class));
    }

    @Test
    void ignoresAnIncompleteEventWithoutThrowing() {
        listener.onStatusChanged(null);
        listener.onStatusChanged(event(null, parcelId, "CREATED", "PICKED_UP", 2));
        listener.onStatusChanged(event(UUID.randomUUID(), parcelId, "CREATED", null, 2));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void forgetsTheOldestEntriesBeyondItsLimit() {
        RecentStatusEvents recent = new RecentStatusEvents();
        ParcelStatusChangedEvent first = event("CREATED", "PICKED_UP", 2);
        assertThat(recent.firstTimeAndNewest(first)).isTrue();

        for (int i = 0; i < RecentStatusEvents.MAX_EVENT_IDS; i++) {
            recent.firstTimeAndNewest(event(UUID.randomUUID(), UUID.randomUUID(), "CREATED", "PICKED_UP", 2));
        }

        // The first event id is forgotten, but the parcel's version still blocks the redelivery.
        assertThat(recent.firstTimeAndNewest(first)).isFalse();
    }
}
