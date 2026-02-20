package com.doodle.mapper;

import com.doodle.domain.TimeSlot;
import com.doodle.dto.response.TimeSlotResponse;
import org.springframework.stereotype.Component;

@Component
public class TimeSlotMapper {

    public TimeSlotResponse toResponse(TimeSlot slot) {
        return new TimeSlotResponse(
                slot.getId(),
                slot.getCalendarId(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getStatus(),
                slot.getCreatedAt(),
                slot.getUpdatedAt()
        );
    }
}
