package com.doodle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.doodle.domain.Calendar;
import com.doodle.domain.SlotStatus;
import com.doodle.domain.TimeSlot;
import com.doodle.dto.request.CreateSlotRequest;
import com.doodle.dto.request.UpdateSlotRequest;
import com.doodle.dto.response.TimeSlotResponse;
import com.doodle.exception.ForbiddenException;
import com.doodle.exception.SlotConflictException;
import com.doodle.mapper.TimeSlotMapper;
import com.doodle.repository.CalendarRepository;
import com.doodle.repository.TimeSlotRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimeSlotServiceTest {

    @Mock
    private TimeSlotRepository slotRepository;

    @Mock
    private CalendarRepository calendarRepository;

    @Mock
    private TimeSlotMapper mapper;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter slotsCreatedCounter;

    private TimeSlotService service;

    @BeforeEach
    void setUp() {
        when(meterRegistry.counter("doodle.slots.created")).thenReturn(slotsCreatedCounter);
        lenient().when(mapper.toResponse(any(TimeSlot.class))).thenAnswer(invocation -> {
            TimeSlot slot = invocation.getArgument(0);
            return new TimeSlotResponse(
                    slot.getId(),
                    slot.getCalendarId(),
                    slot.getStartTime(),
                    slot.getEndTime(),
                    slot.getStatus(),
                    slot.getCreatedAt(),
                    slot.getUpdatedAt()
            );
        });

        service = new TimeSlotService(slotRepository, calendarRepository, mapper, meterRegistry);
    }

    @Test
    void createSlot_whenOverlapExists_throwsConflict() {
        UUID userId = UUID.randomUUID();
        UUID calendarId = UUID.randomUUID();
        Instant start = Instant.parse("2026-04-01T09:00:00Z");
        Instant end = Instant.parse("2026-04-01T10:00:00Z");

        Calendar calendar = new Calendar();
        calendar.setId(calendarId);
        calendar.setUserId(userId);

        when(calendarRepository.findByUserId(userId)).thenReturn(Optional.of(calendar));
        when(slotRepository.existsOverlapping(calendarId, start, end, null)).thenReturn(true);

        assertThatThrownBy(() -> service.createSlot(userId, new CreateSlotRequest(start, end)))
                .isInstanceOf(SlotConflictException.class)
                .hasMessageContaining("overlaps");

        verify(slotRepository, never()).save(any(TimeSlot.class));
    }

    @Test
    void getSlot_whenOwnershipMismatch_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        UUID userCalendarId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID otherCalendarId = UUID.randomUUID();

        Calendar calendar = new Calendar();
        calendar.setId(userCalendarId);
        calendar.setUserId(userId);

        TimeSlot slot = new TimeSlot();
        slot.setId(slotId);
        slot.setCalendarId(otherCalendarId);

        when(calendarRepository.findByUserId(userId)).thenReturn(Optional.of(calendar));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.getSlot(userId, slotId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("access");
    }

    @Test
    void deleteSlot_whenBusy_throwsConflictAndDoesNotDelete() {
        UUID userId = UUID.randomUUID();
        UUID calendarId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        Calendar calendar = new Calendar();
        calendar.setId(calendarId);
        calendar.setUserId(userId);

        TimeSlot busySlot = new TimeSlot();
        busySlot.setId(slotId);
        busySlot.setCalendarId(calendarId);
        busySlot.setStatus(SlotStatus.BUSY);

        when(calendarRepository.findByUserId(userId)).thenReturn(Optional.of(calendar));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(busySlot));

        assertThatThrownBy(() -> service.deleteSlot(userId, slotId))
                .isInstanceOf(SlotConflictException.class)
                .hasMessageContaining("busy slot");

        verify(slotRepository, never()).delete(any(TimeSlot.class));
    }

    @Test
    void updateSlot_whenStatusProvided_transitionsStatus() {
        UUID userId = UUID.randomUUID();
        UUID calendarId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        Calendar calendar = new Calendar();
        calendar.setId(calendarId);
        calendar.setUserId(userId);

        TimeSlot slot = new TimeSlot();
        slot.setId(slotId);
        slot.setCalendarId(calendarId);
        slot.setStartTime(Instant.parse("2026-04-01T09:00:00Z"));
        slot.setEndTime(Instant.parse("2026-04-01T10:00:00Z"));
        slot.setStatus(SlotStatus.FREE);

        when(calendarRepository.findByUserId(userId)).thenReturn(Optional.of(calendar));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(slotRepository.save(any(TimeSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TimeSlotResponse response = service.updateSlot(userId, slotId, new UpdateSlotRequest(null, null, SlotStatus.BUSY));

        assertThat(response.status()).isEqualTo(SlotStatus.BUSY);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BUSY);
        verify(slotRepository).save(eq(slot));
    }
}
