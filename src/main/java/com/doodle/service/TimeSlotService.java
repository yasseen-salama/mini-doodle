package com.doodle.service;

import com.doodle.domain.Calendar;
import com.doodle.domain.SlotStatus;
import com.doodle.domain.TimeSlot;
import com.doodle.dto.request.CreateSlotRequest;
import com.doodle.dto.request.UpdateSlotRequest;
import com.doodle.dto.response.TimeSlotResponse;
import com.doodle.exception.ForbiddenException;
import com.doodle.exception.ResourceNotFoundException;
import com.doodle.exception.SlotConflictException;
import com.doodle.mapper.TimeSlotMapper;
import com.doodle.repository.CalendarRepository;
import com.doodle.repository.TimeSlotRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TimeSlotService {

    private final TimeSlotRepository slotRepository;
    private final CalendarRepository calendarRepository;
    private final TimeSlotMapper mapper;
    private final Counter slotsCreated;

    public TimeSlotService(
            TimeSlotRepository slotRepository,
            CalendarRepository calendarRepository,
            TimeSlotMapper mapper,
            MeterRegistry meterRegistry
    ) {
        this.slotRepository = slotRepository;
        this.calendarRepository = calendarRepository;
        this.mapper = mapper;
        this.slotsCreated = meterRegistry.counter("doodle.slots.created");
    }

    @Transactional
    public TimeSlotResponse createSlot(UUID userId, CreateSlotRequest req) {
        Calendar calendar = getCalendarForUser(userId);
        validateSlotRange(req.startTime(), req.endTime());
        validateNoOverlap(calendar.getId(), req.startTime(), req.endTime(), null);

        TimeSlot slot = new TimeSlot();
        slot.setId(UUID.randomUUID());
        slot.setCalendarId(calendar.getId());
        slot.setStartTime(req.startTime());
        slot.setEndTime(req.endTime());
        slot.setStatus(SlotStatus.FREE);
        TimeSlot saved = slotRepository.save(slot);
        slotsCreated.increment();
        return mapper.toResponse(saved);
    }

    public Page<TimeSlotResponse> getSlotsInRange(UUID userId, Instant from, Instant to, Pageable pageable) {
        Calendar calendar = getCalendarForUser(userId);
        validateWindow(from, to);
        return slotRepository.findByCalendarAndRange(calendar.getId(), from, to, pageable)
                .map(mapper::toResponse);
    }

    public TimeSlotResponse getSlot(UUID userId, UUID slotId) {
        return mapper.toResponse(getSlotWithOwnershipCheck(userId, slotId));
    }

    @Transactional
    public TimeSlotResponse updateSlot(UUID userId, UUID slotId, UpdateSlotRequest req) {
        TimeSlot slot = getSlotWithOwnershipCheck(userId, slotId);

        if (req.startTime() != null || req.endTime() != null) {
            Instant newStart = req.startTime() != null ? req.startTime() : slot.getStartTime();
            Instant newEnd = req.endTime() != null ? req.endTime() : slot.getEndTime();
            validateSlotRange(newStart, newEnd);
            validateNoOverlap(slot.getCalendarId(), newStart, newEnd, slotId);
            slot.setStartTime(newStart);
            slot.setEndTime(newEnd);
        }

        if (req.status() != null) {
            slot.setStatus(req.status());
        }

        return mapper.toResponse(slotRepository.save(slot));
    }

    @Transactional
    public void deleteSlot(UUID userId, UUID slotId) {
        TimeSlot slot = getSlotWithOwnershipCheck(userId, slotId);
        if (slot.getStatus() == SlotStatus.BUSY) {
            throw new SlotConflictException("Cannot delete a busy slot. Cancel the meeting first.");
        }
        slotRepository.delete(slot);
    }

    private Calendar getCalendarForUser(UUID userId) {
        return calendarRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar not found for user"));
    }

    private TimeSlot getSlotWithOwnershipCheck(UUID userId, UUID slotId) {
        Calendar calendar = getCalendarForUser(userId);
        TimeSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Time slot not found"));
        if (!slot.getCalendarId().equals(calendar.getId())) {
            throw new ForbiddenException("You do not have access to this slot");
        }
        return slot;
    }

    private void validateWindow(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
    }

    private void validateSlotRange(Instant start, Instant end) {
        validateWindow(start, end);
        if (Duration.between(start, end).toMinutes() < 15) {
            throw new IllegalArgumentException("Slot must be at least 15 minutes");
        }
    }

    private void validateNoOverlap(UUID calendarId, Instant start, Instant end, UUID excludeId) {
        if (slotRepository.existsOverlapping(calendarId, start, end, excludeId)) {
            throw new SlotConflictException("Time slot overlaps with an existing slot");
        }
    }
}
