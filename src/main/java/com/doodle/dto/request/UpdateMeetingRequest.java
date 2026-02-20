package com.doodle.dto.request;

import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record UpdateMeetingRequest(
        @Size(max = 255) String title,
        String description,
        Set<UUID> participantIds
) {
}
