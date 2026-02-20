package com.doodle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AvailabilityIntegrationTest extends AbstractIntegrationTest {

    @Test
    void getAvailability_returnsFreeAndBusyWindows() {
        TestUser targetUser = registerUser("availability-target");
        TestUser requester = registerUser("availability-requester");

        createSlot(targetUser, "2026-04-14T09:00:00Z", "2026-04-14T10:00:00Z");
        UUID busySlot = createSlot(targetUser, "2026-04-14T10:30:00Z", "2026-04-14T11:30:00Z");
        ResponseEntity<String> meetingResponse = scheduleMeeting(targetUser, busySlot, "availability-busy");
        assertThat(meetingResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> response = get(
                "/api/availability?userId=" + targetUser.id()
                        + "&from=2026-04-14T00:00:00Z&to=2026-04-15T00:00:00Z",
                requester
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = readJsonBody(response);
        assertThat(body.get("userId").asText()).isEqualTo(targetUser.id().toString());
        assertThat(body.get("windows").isArray()).isTrue();
        assertThat(body.get("windows").size()).isEqualTo(2);

        Set<String> statuses = new HashSet<>();
        for (JsonNode window : body.get("windows")) {
            statuses.add(window.get("status").asText());
        }
        assertThat(statuses).contains("FREE", "BUSY");
    }

    @Test
    void getAvailability_withInvalidRange_returnsBadRequest() {
        TestUser user = registerUser("availability-invalid");

        ResponseEntity<String> response = get(
                "/api/availability?userId=" + user.id()
                        + "&from=2026-04-15T12:00:00Z&to=2026-04-15T09:00:00Z",
                user
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readJsonBody(response).get("message").asText()).contains("to must be after from");
    }

    @Test
    void availabilityEndpoint_withoutAuthentication_returnsUnauthorized() {
        UUID userId = UUID.randomUUID();
        ResponseEntity<String> response = get(
                "/api/availability?userId=" + userId
                        + "&from=2026-04-14T00:00:00Z&to=2026-04-15T00:00:00Z",
                null
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
