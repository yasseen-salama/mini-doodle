package com.doodle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TimeSlotIntegrationTest extends AbstractIntegrationTest {

    @Test
    void createSlot_returnsCreated() {
        TestUser user = registerUser("slot-create");

        ResponseEntity<String> response = post(
                "/api/slots",
                Map.of("startTime", "2026-04-01T09:00:00Z", "endTime", "2026-04-01T10:00:00Z"),
                user
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = readJsonBody(response);
        assertThat(body.get("id").asText()).isNotBlank();
        assertThat(body.get("status").asText()).isEqualTo("FREE");
    }

    @Test
    void createOverlappingSlot_returnsConflict() {
        TestUser user = registerUser("slot-overlap");
        createSlot(user, "2026-04-01T09:00:00Z", "2026-04-01T10:00:00Z");

        ResponseEntity<String> response = post(
                "/api/slots",
                Map.of("startTime", "2026-04-01T09:30:00Z", "endTime", "2026-04-01T10:30:00Z"),
                user
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readJsonBody(response).get("message").asText()).contains("overlaps");
    }

    @Test
    void deleteSlotWithMeeting_returnsConflict() {
        TestUser user = registerUser("slot-delete");
        UUID slotId = createSlot(user, "2026-04-01T11:00:00Z", "2026-04-01T12:00:00Z");
        ResponseEntity<String> meetingResponse = scheduleMeeting(user, slotId, "slot-delete-meeting");
        assertThat(meetingResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID meetingId = UUID.fromString(readJsonBody(meetingResponse).get("id").asText());

        ResponseEntity<String> deleteSlotResponse = delete("/api/slots/" + slotId, user);

        assertThat(deleteSlotResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readJsonBody(deleteSlotResponse).get("message").asText()).contains("busy slot");

        ResponseEntity<String> cancelMeetingResponse = delete("/api/meetings/" + meetingId, user);
        assertThat(cancelMeetingResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void getSlotsByRange_returnsPaginatedResults() {
        TestUser user = registerUser("slot-page");
        createSlot(user, "2026-04-01T09:00:00Z", "2026-04-01T10:00:00Z");
        createSlot(user, "2026-04-01T10:15:00Z", "2026-04-01T11:00:00Z");

        ResponseEntity<String> response = get(
                "/api/slots?from=2026-04-01T00:00:00Z&to=2026-04-02T00:00:00Z&page=0&size=1",
                user
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = readJsonBody(response);
        assertThat(body.get("page").asInt()).isEqualTo(0);
        assertThat(body.get("size").asInt()).isEqualTo(1);
        assertThat(body.get("totalElements").asInt()).isEqualTo(2);
        assertThat(body.get("totalPages").asInt()).isEqualTo(2);
        assertThat(body.get("content").isArray()).isTrue();
        assertThat(body.get("content").size()).isEqualTo(1);
    }

    @Test
    void updateSlot_partialUpdate_returnsUpdatedSlot() {
        TestUser user = registerUser("slot-update");
        UUID slotId = createSlot(user, "2026-04-04T09:00:00Z", "2026-04-04T10:00:00Z");

        ResponseEntity<String> response = patch(
                "/api/slots/" + slotId,
                Map.of(
                        "startTime", "2026-04-04T09:15:00Z",
                        "endTime", "2026-04-04T10:30:00Z",
                        "status", "BUSY"
                ),
                user
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = readJsonBody(response);
        assertThat(body.get("id").asText()).isEqualTo(slotId.toString());
        assertThat(Instant.parse(body.get("startTime").asText())).isEqualTo(Instant.parse("2026-04-04T09:15:00Z"));
        assertThat(Instant.parse(body.get("endTime").asText())).isEqualTo(Instant.parse("2026-04-04T10:30:00Z"));
        assertThat(body.get("status").asText()).isEqualTo("BUSY");
    }

    @Test
    void updateSlot_whenOwnedByAnotherUser_returnsForbidden() {
        TestUser owner = registerUser("slot-owner");
        TestUser otherUser = registerUser("slot-owner-other");
        UUID slotId = createSlot(owner, "2026-04-05T09:00:00Z", "2026-04-05T10:00:00Z");

        ResponseEntity<String> response = patch(
                "/api/slots/" + slotId,
                Map.of("status", "BUSY"),
                otherUser
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(readJsonBody(response).get("message").asText()).contains("access to this slot");
    }

    @Test
    void createSlot_withInvalidDuration_returnsBadRequest() {
        TestUser user = registerUser("slot-invalid-duration");

        ResponseEntity<String> response = post(
                "/api/slots",
                Map.of("startTime", "2026-04-06T09:00:00Z", "endTime", "2026-04-06T09:10:00Z"),
                user
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readJsonBody(response).get("message").asText()).contains("at least 15 minutes");
    }

    @Test
    void updateSlot_withInvalidRange_returnsBadRequest() {
        TestUser user = registerUser("slot-invalid-range");
        UUID slotId = createSlot(user, "2026-04-06T10:00:00Z", "2026-04-06T11:00:00Z");

        ResponseEntity<String> response = patch(
                "/api/slots/" + slotId,
                Map.of("startTime", "2026-04-06T11:30:00Z", "endTime", "2026-04-06T11:00:00Z"),
                user
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readJsonBody(response).get("message").asText()).contains("endTime must be after startTime");
    }

    @Test
    void protectedSlotsEndpoint_withoutAuthentication_returnsUnauthorized() {
        ResponseEntity<String> response = get(
                "/api/slots?from=2026-04-01T00:00:00Z&to=2026-04-02T00:00:00Z&page=0&size=20",
                null
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
