package com.doodle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
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

    @Test
    void getMeetings_returnsOrganizerAndParticipantResults() {
        TestUser organizer = registerUser("meeting-list-organizer");
        TestUser participant = registerUser("meeting-list-participant");
        UUID slotId = createSlot(organizer, "2026-04-07T09:00:00Z", "2026-04-07T10:00:00Z");

        ResponseEntity<String> scheduleResponse = scheduleMeeting(
                organizer,
                slotId,
                "list-meeting",
                List.of(participant.id())
        );
        assertThat(scheduleResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID meetingId = UUID.fromString(readJsonBody(scheduleResponse).get("id").asText());

        ResponseEntity<String> organizerListResponse = get(
                "/api/meetings?from=2026-04-07T00:00:00Z&to=2026-04-08T00:00:00Z&page=0&size=20",
                organizer
        );
        assertThat(organizerListResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentContainsMeetingId(readJsonBody(organizerListResponse), meetingId)).isTrue();

        ResponseEntity<String> participantListResponse = get(
                "/api/meetings?from=2026-04-07T00:00:00Z&to=2026-04-08T00:00:00Z&page=0&size=20",
                participant
        );
        assertThat(participantListResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentContainsMeetingId(readJsonBody(participantListResponse), meetingId)).isTrue();
    }

    @Test
    void getMeeting_returnsDetailsForOrganizerAndParticipant() {
        TestUser organizer = registerUser("meeting-get-organizer");
        TestUser participant = registerUser("meeting-get-participant");
        UUID slotId = createSlot(organizer, "2026-04-08T09:00:00Z", "2026-04-08T10:00:00Z");

        ResponseEntity<String> scheduleResponse = scheduleMeeting(
                organizer,
                slotId,
                "detailed-meeting",
                List.of(participant.id())
        );
        assertThat(scheduleResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID meetingId = UUID.fromString(readJsonBody(scheduleResponse).get("id").asText());

        ResponseEntity<String> organizerView = get("/api/meetings/" + meetingId, organizer);
        assertThat(organizerView.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode organizerBody = readJsonBody(organizerView);
        assertThat(organizerBody.get("id").asText()).isEqualTo(meetingId.toString());
        assertThat(organizerBody.get("title").asText()).isEqualTo("detailed-meeting");

        ResponseEntity<String> participantView = get("/api/meetings/" + meetingId, participant);
        assertThat(participantView.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJsonBody(participantView).get("id").asText()).isEqualTo(meetingId.toString());
    }

    @Test
    void updateMeeting_updatesTitleDescriptionAndParticipants() {
        TestUser organizer = registerUser("meeting-update-organizer");
        TestUser participantOne = registerUser("meeting-update-p1");
        TestUser participantTwo = registerUser("meeting-update-p2");
        UUID slotId = createSlot(organizer, "2026-04-09T09:00:00Z", "2026-04-09T10:00:00Z");

        ResponseEntity<String> scheduleResponse = scheduleMeeting(
                organizer,
                slotId,
                "initial-title",
                List.of(participantOne.id())
        );
        assertThat(scheduleResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID meetingId = UUID.fromString(readJsonBody(scheduleResponse).get("id").asText());

        ResponseEntity<String> updateResponse = patch(
                "/api/meetings/" + meetingId,
                Map.of(
                        "title", "updated-title",
                        "description", "updated-description",
                        "participantIds", List.of(participantOne.id(), participantTwo.id())
                ),
                organizer
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode updated = readJsonBody(updateResponse);
        assertThat(updated.get("title").asText()).isEqualTo("updated-title");
        assertThat(updated.get("description").asText()).isEqualTo("updated-description");
        assertThat(arrayContainsText(updated.get("participantIds"), participantOne.id().toString())).isTrue();
        assertThat(arrayContainsText(updated.get("participantIds"), participantTwo.id().toString())).isTrue();
    }

    @Test
    void getMeeting_withoutAccess_returnsForbidden() {
        TestUser organizer = registerUser("meeting-access-organizer");
        TestUser outsider = registerUser("meeting-access-outsider");
        UUID slotId = createSlot(organizer, "2026-04-10T09:00:00Z", "2026-04-10T10:00:00Z");

        ResponseEntity<String> scheduleResponse = scheduleMeeting(organizer, slotId, "private-meeting");
        assertThat(scheduleResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID meetingId = UUID.fromString(readJsonBody(scheduleResponse).get("id").asText());

        ResponseEntity<String> outsiderResponse = get("/api/meetings/" + meetingId, outsider);

        assertThat(outsiderResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(readJsonBody(outsiderResponse).get("message").asText()).contains("access to this meeting");
    }

    @Test
    void updateMeeting_whenNotOrganizer_returnsForbidden() {
        TestUser organizer = registerUser("meeting-owner-organizer");
        TestUser participant = registerUser("meeting-owner-participant");
        UUID slotId = createSlot(organizer, "2026-04-11T09:00:00Z", "2026-04-11T10:00:00Z");

        ResponseEntity<String> scheduleResponse = scheduleMeeting(
                organizer,
                slotId,
                "organizer-only",
                List.of(participant.id())
        );
        assertThat(scheduleResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID meetingId = UUID.fromString(readJsonBody(scheduleResponse).get("id").asText());

        ResponseEntity<String> participantPatchResponse = patch(
                "/api/meetings/" + meetingId,
                Map.of("title", "participant-change"),
                participant
        );

        assertThat(participantPatchResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(readJsonBody(participantPatchResponse).get("message").asText()).contains("Only organizer");
    }

    @Test
    void getMeetings_withInvalidRange_returnsBadRequest() {
        TestUser organizer = registerUser("meeting-range-invalid");

        ResponseEntity<String> response = get(
                "/api/meetings?from=2026-04-12T12:00:00Z&to=2026-04-12T09:00:00Z&page=0&size=20",
                organizer
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readJsonBody(response).get("message").asText()).contains("to must be after from");
    }

    @Test
    void scheduleMeeting_withBlankTitle_returnsBadRequest() {
        TestUser organizer = registerUser("meeting-title-invalid");
        UUID slotId = createSlot(organizer, "2026-04-13T09:00:00Z", "2026-04-13T10:00:00Z");

        ResponseEntity<String> response = post(
                "/api/meetings",
                Map.of(
                        "slotId", slotId,
                        "title", "   ",
                        "description", "invalid",
                        "participantIds", List.of()
                ),
                organizer
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readJsonBody(response).get("message").asText()).contains("title");
    }

    @Test
    void protectedMeetingsEndpoint_withoutAuthentication_returnsUnauthorized() {
        ResponseEntity<String> response = get(
                "/api/meetings?from=2026-04-07T00:00:00Z&to=2026-04-08T00:00:00Z&page=0&size=20",
                null
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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

    private boolean contentContainsMeetingId(JsonNode pageBody, UUID meetingId) {
        JsonNode content = pageBody.get("content");
        if (content == null || !content.isArray()) {
            return false;
        }
        for (JsonNode item : content) {
            if (meetingId.toString().equals(item.get("id").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean arrayContainsText(JsonNode arrayNode, String expectedValue) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return false;
        }
        for (JsonNode node : arrayNode) {
            if (expectedValue.equals(node.asText())) {
                return true;
            }
        }
        return false;
    }
}
