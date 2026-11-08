package com.swiftdrop.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.common.exception.RateLimitExceededException;
import com.swiftdrop.realtime.dto.LiveStatusUpdate;
import com.swiftdrop.tracking.config.TrackingProperties;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class LiveStreamHubTest {

    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(10);
    private static final int MAX_STREAMS_PER_CLIENT = 2;

    /** Records what the hub sends and lets the test fire the emitter's callbacks. */
    static class FakeEmitter extends SseEmitter {

        final List<String> sent = new ArrayList<>();
        final List<Object> data = new ArrayList<>();
        boolean failOnSend;
        boolean completed;
        Runnable onCompletion = () -> { };
        Runnable onTimeout = () -> { };
        Consumer<Throwable> onError = error -> { };

        FakeEmitter(long timeout) {
            super(timeout);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failOnSend) {
                throw new IOException("Broken pipe");
            }
            StringBuilder text = new StringBuilder();
            for (var part : builder.build()) {
                if (part.getData() instanceof String s) {
                    text.append(s);
                } else {
                    data.add(part.getData());
                    text.append("<data>");
                }
            }
            sent.add(text.toString());
        }

        @Override
        public void complete() {
            completed = true;
        }

        @Override
        public void onCompletion(Runnable callback) {
            onCompletion = callback;
        }

        @Override
        public void onTimeout(Runnable callback) {
            onTimeout = callback;
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
            onError = callback;
        }

        boolean sentEvent(String eventName) {
            return sent.stream().anyMatch(event -> event.startsWith("event:" + eventName + "\n"));
        }

        boolean sentComment(String comment) {
            return sent.contains(":" + comment + "\n\n");
        }
    }

    private final List<FakeEmitter> emitters = new ArrayList<>();
    private final List<Long> timeouts = new ArrayList<>();
    private LiveStreamHub hub;

    @BeforeEach
    void setUp() {
        var properties = new TrackingProperties(null, null, MAX_STREAMS_PER_CLIENT, STREAM_TIMEOUT,
                Duration.ofSeconds(5));
        hub = new LiveStreamHub(properties) {
            @Override
            SseEmitter newEmitter(long timeoutMillis) {
                timeouts.add(timeoutMillis);
                FakeEmitter emitter = new FakeEmitter(timeoutMillis);
                emitters.add(emitter);
                return emitter;
            }
        };
    }

    private FakeEmitter parcelStream(String trackingNumber, String client) {
        return (FakeEmitter) hub.openParcelStream(trackingNumber, client);
    }

    private FakeEmitter adminStream(String client) {
        return (FakeEmitter) hub.openAdminStream(client);
    }

    private static LiveStatusUpdate update(String trackingNumber, String status, long version) {
        return new LiveStatusUpdate(trackingNumber, "CREATED", status, version, Instant.parse("2026-11-07T04:30:00Z"));
    }

    @Test
    void openingAStreamRegistersItAndSendsAConnectedComment() {
        FakeEmitter stream = parcelStream("SD1", "10.0.0.1");

        assertThat(hub.openStreamCount()).isEqualTo(1);
        assertThat(stream.sentComment("connected")).isTrue();
        assertThat(timeouts).containsExactly(STREAM_TIMEOUT.toMillis());
    }

    @Test
    void anUpdateReachesThatParcelsStreamsAndEveryAdminStream() {
        FakeEmitter watchingSd1 = parcelStream("SD1", "10.0.0.1");
        FakeEmitter watchingSd2 = parcelStream("SD2", "10.0.0.2");
        FakeEmitter admin = adminStream("10.0.0.3");
        LiveStatusUpdate pickedUp = update("SD1", "PICKED_UP", 2);

        hub.broadcast(pickedUp);

        assertThat(watchingSd1.sentEvent(LiveStatusUpdate.EVENT_NAME)).isTrue();
        assertThat(watchingSd1.data).containsExactly(pickedUp);
        assertThat(watchingSd2.sentEvent(LiveStatusUpdate.EVENT_NAME)).isFalse();
        assertThat(admin.data).containsExactly(pickedUp);
    }

    @Test
    void aClientCanHoldOnlyItsMaximumNumberOfStreams() {
        parcelStream("SD1", "10.0.0.1");
        adminStream("10.0.0.1");

        assertThatThrownBy(() -> parcelStream("SD2", "10.0.0.1"))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> assertThat(((RateLimitExceededException) ex).getRetryAfter())
                        .isEqualTo(LiveStreamHub.TOO_MANY_STREAMS_RETRY_AFTER));
        // Another client is not affected.
        parcelStream("SD2", "10.0.0.2");
        assertThat(hub.openStreamCount()).isEqualTo(3);
    }

    @Test
    void aCompletedStreamIsForgottenAndFreesItsPlace() {
        FakeEmitter first = parcelStream("SD1", "10.0.0.1");
        parcelStream("SD1", "10.0.0.1");

        first.onCompletion.run();

        assertThat(hub.openStreamCount()).isEqualTo(1);
        parcelStream("SD1", "10.0.0.1");
        hub.broadcast(update("SD1", "PICKED_UP", 2));
        assertThat(first.data).isEmpty();
    }

    @Test
    void aStreamThatFailsIsForgotten() {
        FakeEmitter stream = parcelStream("SD1", "10.0.0.1");

        stream.onError.accept(new IOException("Connection reset"));

        assertThat(hub.openStreamCount()).isZero();
    }

    @Test
    void anExpiredStreamIsCompletedAndForgotten() {
        FakeEmitter stream = parcelStream("SD1", "10.0.0.1");

        stream.onTimeout.run();

        assertThat(stream.completed).isTrue();
        assertThat(hub.openStreamCount()).isZero();
    }

    @Test
    void aStreamThatCanNoLongerBeWrittenToIsDroppedWithoutStoppingTheOthers() {
        FakeEmitter gone = parcelStream("SD1", "10.0.0.1");
        FakeEmitter open = parcelStream("SD1", "10.0.0.2");
        gone.failOnSend = true;

        hub.broadcast(update("SD1", "PICKED_UP", 2));

        assertThat(open.data).hasSize(1);
        assertThat(hub.openStreamCount()).isEqualTo(1);
    }

    @Test
    void heartbeatsPingEveryStreamAndDropTheDeadOnes() {
        FakeEmitter parcel = parcelStream("SD1", "10.0.0.1");
        FakeEmitter admin = adminStream("10.0.0.2");
        FakeEmitter dead = parcelStream("SD2", "10.0.0.3");
        dead.failOnSend = true;

        hub.sendHeartbeats();

        assertThat(parcel.sentComment("ping")).isTrue();
        assertThat(admin.sentComment("ping")).isTrue();
        assertThat(hub.openStreamCount()).isEqualTo(2);
    }

    @Test
    void shuttingDownCompletesEveryStream() {
        FakeEmitter parcel = parcelStream("SD1", "10.0.0.1");
        FakeEmitter admin = adminStream("10.0.0.2");

        hub.closeAll();

        assertThat(parcel.completed).isTrue();
        assertThat(admin.completed).isTrue();
        assertThat(hub.openStreamCount()).isZero();
    }
}
