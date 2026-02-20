package com.doodle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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
}
