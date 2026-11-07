package com.swiftdrop.realtime.event;

import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Remembers the latest status events handled by this backend instance, so a duplicate (delivery
 * is at least once) or an older event that arrives after a newer one is not pushed to browsers.
 *
 * <p>In memory and bounded: the oldest entries are forgotten first. That is enough for live pushes,
 * which only keep open screens fresh; browsers reload the real state over REST on reconnect.
 */
@Component
public class RecentStatusEvents {

    /** Event ids remembered to skip exact redeliveries. */
    static final int MAX_EVENT_IDS = 2_000;

    /** Parcels whose latest status version is remembered. */
    static final int MAX_PARCELS = 10_000;

    private final Map<UUID, Boolean> eventIds = boundedMap(MAX_EVENT_IDS);
    private final Map<UUID, Long> latestVersionByParcel = boundedMap(MAX_PARCELS);

    /**
     * Records the event and says whether it should be pushed: {@code false} for an event id seen
     * before, or a status version that isn't newer than the latest one handled for the parcel.
     */
    public synchronized boolean firstTimeAndNewest(ParcelStatusChangedEvent event) {
        if (eventIds.putIfAbsent(event.eventId(), Boolean.TRUE) != null) {
            return false;
        }
        Long latest = latestVersionByParcel.get(event.parcelId());
        if (latest != null && event.statusVersion() <= latest) {
            return false;
        }
        latestVersionByParcel.put(event.parcelId(), event.statusVersion());
        return true;
    }

    /** Insertion-ordered map that drops its oldest entry beyond {@code maxEntries}. */
    private static <K, V> Map<K, V> boundedMap(int maxEntries) {
        return new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        };
    }
}
