package com.doodle.dto.request;

import com.doodle.domain.SlotStatus;
import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import java.time.Instant;

public record UpdateSlotRequest(
        Instant startTime,
        Instant endTime,
        SlotStatus status
) {

    @AssertTrue(message = "endTime must be after startTime")
    public boolean isValidRange() {
        if (startTime == null || endTime == null) {
            return true;
        }
        return endTime.isAfter(startTime);
    }

    @AssertTrue(message = "Slot must be at least 15 minutes")
    public boolean isMinDuration() {
        if (startTime == null || endTime == null) {
            return true;
        }
        return Duration.between(startTime, endTime).toMinutes() >= 15;
    }
}
