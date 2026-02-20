package com.doodle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class MeetingIntegrationTest extends AbstractIntegrationTest {

    @Test
    void scheduleMeeting_setsSlotToBusy() {
        TestUser organizer = registerUser("meeting-organizer");
        UUID slotId = createSlot(organizer, "2026-04-02T09:00:00Z", "2026-04-02T10:00:00Z");

        ResponseEntity<String> response = scheduleMeeting(organizer, slotId, "daily");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode meeting = readJsonBody(response);
        assertThat(meeting.get("slotId").asText()).isEqualTo(slotId.toString());

        ResponseEntity<String> slotResponse = get("/api/slots/" + slotId, organizer);
        assertThat(slotResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJsonBody(slotResponse).get("status").asText()).isEqualTo("BUSY");
    }

    @Test
    void cancelMeeting_revertsSlotToFree() {
        TestUser organizer = registerUser("meeting-cancel");
        UUID slotId = createSlot(organizer, "2026-04-02T10:30:00Z", "2026-04-02T11:30:00Z");

        ResponseEntity<String> scheduleResponse = scheduleMeeting(organizer, slotId, "cancel-meeting");
        assertThat(scheduleResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID meetingId = UUID.fromString(readJsonBody(scheduleResponse).get("id").asText());

        ResponseEntity<String> cancelResponse = delete("/api/meetings/" + meetingId, organizer);
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> slotResponse = get("/api/slots/" + slotId, organizer);
        assertThat(slotResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJsonBody(slotResponse).get("status").asText()).isEqualTo("FREE");
    }

    @Test
    void scheduleOnBusySlot_returnsConflict() {
        TestUser organizer = registerUser("meeting-busy");
        UUID slotId = createSlot(organizer, "2026-04-02T12:00:00Z", "2026-04-02T13:00:00Z");

        ResponseEntity<String> firstResponse = scheduleMeeting(organizer, slotId, "first");
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> secondResponse = scheduleMeeting(organizer, slotId, "second");
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readJsonBody(secondResponse).get("message").asText()).contains("busy");
    }

    @Test
    void concurrentBooking_onlyOneSucceeds() throws InterruptedException {
        TestUser organizer = registerUser("meeting-concurrency");
        UUID slotId = createSlot(organizer, "2026-04-03T09:00:00Z", "2026-04-03T10:00:00Z");

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicInteger others = new AtomicInteger();

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            final int index = i;
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    int status = scheduleMeetingStatus(organizer, slotId, "concurrent-" + index);
                    if (status == HttpStatus.CREATED.value()) {
                        successes.incrementAndGet();
                    } else if (status == HttpStatus.CONFLICT.value()) {
                        conflicts.incrementAndGet();
                    } else {
                        others.incrementAndGet();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    others.incrementAndGet();
                }
            });
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(9);
        assertThat(others.get()).isZero();
    }

    private int scheduleMeetingStatus(TestUser user, UUID slotId, String title) {
        ResponseEntity<String> response = exchange(
                HttpMethod.POST,
                "/api/meetings",
                Map.of(
                        "slotId", slotId,
                        "title", title,
                        "description", "concurrency test",
                        "participantIds", java.util.List.of()
                ),
                user
        );
        return response.getStatusCode().value();
    }
}
