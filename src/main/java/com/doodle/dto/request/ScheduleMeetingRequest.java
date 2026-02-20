package com.doodle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record ScheduleMeetingRequest(
        @NotNull UUID slotId,
        @NotBlank @Size(max = 255) String title,
        String description,
        Set<UUID> participantIds
) {
}
