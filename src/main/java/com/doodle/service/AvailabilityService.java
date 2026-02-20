package com.doodle.service;

import com.doodle.domain.Calendar;
import com.doodle.domain.TimeSlot;
import com.doodle.dto.response.AvailabilityResponse;
import com.doodle.dto.response.SlotWindow;
import com.doodle.exception.ResourceNotFoundException;
import com.doodle.repository.CalendarRepository;
import com.doodle.repository.TimeSlotRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    private final CalendarRepository calendarRepository;
    private final TimeSlotRepository slotRepository;

    public AvailabilityService(CalendarRepository calendarRepository, TimeSlotRepository slotRepository) {
        this.calendarRepository = calendarRepository;
        this.slotRepository = slotRepository;
    }

    public AvailabilityResponse getAvailability(UUID targetUserId, Instant from, Instant to) {
        validateWindow(from, to);

        Calendar calendar = calendarRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar not found for user"));

        List<TimeSlot> slots = slotRepository.findByCalendarAndRange(
                calendar.getId(), from, to, Pageable.unpaged()
        ).getContent();

        List<SlotWindow> windows = slots.stream()
                .map(slot -> new SlotWindow(slot.getStartTime(), slot.getEndTime(), slot.getStatus()))
                .toList();

        return new AvailabilityResponse(targetUserId, from, to, windows);
    }

    private void validateWindow(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("to must be after from");
        }
    }
}
