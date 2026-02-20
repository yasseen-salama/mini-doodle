package com.doodle.dto.response;

import com.doodle.domain.SlotStatus;
import java.time.Instant;

public record SlotWindow(
        Instant startTime,
        Instant endTime,
        SlotStatus status
) {
}
