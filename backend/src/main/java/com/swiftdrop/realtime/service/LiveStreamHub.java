package com.swiftdrop.realtime.service;

import com.swiftdrop.common.exception.RateLimitExceededException;
import com.swiftdrop.realtime.dto.LiveStatusUpdate;
import com.swiftdrop.tracking.config.TrackingProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The open live streams of this backend instance and who gets which update.
 *
 * <ul>
 *   <li>A <b>parcel stream</b> gets the updates of one tracking number (public tracking page).</li>
 *   <li>An <b>admin stream</b> gets every update (admin Deliveries Tracking board).</li>
 * </ul>
 *
 * <p>Each client IP may hold {@code swiftdrop.tracking.max-streams-per-client} streams at once. A
 * stream is forgotten as soon as it completes, fails or can't be written to. Streams live only in
 * memory: a restart closes them and browsers reconnect.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveStreamHub {

    /** Suggested wait when a client already has its maximum number of streams open. */
    static final Duration TOO_MANY_STREAMS_RETRY_AFTER = Duration.ofSeconds(30);

    private final TrackingProperties properties;

    private final Set<LiveStream> streams = ConcurrentHashMap.newKeySet();

    /**
     * One open stream.
     *
     * @param trackingNumber the parcel it follows, or {@code null} for an admin stream (every parcel)
     */
    record LiveStream(SseEmitter emitter, String clientKey, String trackingNumber) {

        boolean receives(LiveStatusUpdate update) {
            return trackingNumber == null || trackingNumber.equals(update.trackingNumber());
        }
    }

    /**
     * A stream of the updates of one parcel.
     *
     * @param trackingNumber the stored (normalised) tracking number
     * @param clientKey      who opened it, for the per-client limit (the client IP)
     * @throws RateLimitExceededException if the client already has its maximum number of streams open
     */
    public SseEmitter openParcelStream(String trackingNumber, String clientKey) {
        return open(trackingNumber, clientKey);
    }

    /**
     * A stream of every parcel's updates, for admins. The caller checks the role.
     *
     * @throws RateLimitExceededException if the client already has its maximum number of streams open
     */
    public SseEmitter openAdminStream(String clientKey) {
        return open(null, clientKey);
    }

    /** Pushes the update to every stream that follows its parcel and to every admin stream. */
    @EventListener
    public void broadcast(LiveStatusUpdate update) {
        for (LiveStream stream : streams) {
            if (stream.receives(update)) {
                send(stream, SseEmitter.event()
                        .name(LiveStatusUpdate.EVENT_NAME)
                        .data(update, MediaType.APPLICATION_JSON));
            }
        }
    }

    /** Number of open streams on this instance. */
    public int openStreamCount() {
        return streams.size();
    }

    /** Builds the emitter; a test can replace it to watch what is sent. */
    SseEmitter newEmitter(long timeoutMillis) {
        return new SseEmitter(timeoutMillis);
    }

    // The limit check and the registration happen together, so two requests can't both pass it.
    private synchronized SseEmitter open(String trackingNumber, String clientKey) {
        long open = streams.stream().filter(s -> s.clientKey().equals(clientKey)).count();
        if (open >= properties.maxStreamsPerClient()) {
            throw new RateLimitExceededException("Too many live updates are open from this connection.",
                    TOO_MANY_STREAMS_RETRY_AFTER);
        }
        SseEmitter emitter = newEmitter(properties.streamTimeout().toMillis());
        LiveStream stream = new LiveStream(emitter, clientKey, trackingNumber);
        emitter.onCompletion(() -> streams.remove(stream));
        emitter.onError(error -> streams.remove(stream));
        streams.add(stream);
        // Sent first so the response (and its headers) reaches the browser straight away.
        send(stream, SseEmitter.event().comment("connected"));
        return emitter;
    }

    /** Sends one event; a stream that can't be written to is closed and forgotten. */
    void send(LiveStream stream, SseEmitter.SseEventBuilder event) {
        try {
            stream.emitter().send(event);
        } catch (IOException | IllegalStateException ex) {
            // The browser went away (IOException) or the stream was already completed.
            streams.remove(stream);
            log.debug("Closed a live stream that could not be written to: {}", ex.getClass().getSimpleName());
        }
    }
}
