package com.doodle.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record MeetingResponse(
        UUID id,
        UUID slotId,
        UUID organizerId,
        String title,
        String description,
        Instant createdAt,
        Set<UUID> participantIds
) {
}
