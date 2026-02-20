package com.doodle.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AvailabilityResponse(
        UUID userId,
        Instant from,
        Instant to,
        List<SlotWindow> windows
) {
}
