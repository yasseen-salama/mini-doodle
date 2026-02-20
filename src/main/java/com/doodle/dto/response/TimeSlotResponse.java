package com.doodle.dto.response;

import com.doodle.domain.SlotStatus;
import java.time.Instant;
import java.util.UUID;

public record TimeSlotResponse(
        UUID id,
        UUID calendarId,
        Instant startTime,
        Instant endTime,
        SlotStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
