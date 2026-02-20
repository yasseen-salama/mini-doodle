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
import com.doodle.domain.Meeting;
import com.doodle.domain.SlotStatus;
import com.doodle.domain.TimeSlot;
import com.doodle.dto.request.ScheduleMeetingRequest;
import com.doodle.dto.response.MeetingResponse;
import com.doodle.exception.SlotConflictException;
import com.doodle.mapper.MeetingMapper;
import com.doodle.repository.CalendarRepository;
import com.doodle.repository.MeetingRepository;
import com.doodle.repository.TimeSlotRepository;
import com.doodle.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private TimeSlotRepository slotRepository;

    @Mock
    private CalendarRepository calendarRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MeetingMapper mapper;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter meetingsScheduledCounter;

    private MeetingService service;

    @BeforeEach
    void setUp() {
        when(meterRegistry.counter("doodle.meetings.scheduled")).thenReturn(meetingsScheduledCounter);
        lenient().when(mapper.toResponse(any(Meeting.class))).thenAnswer(invocation -> {
            Meeting meeting = invocation.getArgument(0);
            return new MeetingResponse(
                    meeting.getId(),
                    meeting.getSlotId(),
                    meeting.getOrganizerId(),
                    meeting.getTitle(),
                    meeting.getDescription(),
                    meeting.getCreatedAt(),
                    meeting.getParticipants().stream()
                            .map(user -> user.getId())
                            .collect(Collectors.toSet())
            );
        });

        service = new MeetingService(
                meetingRepository,
                slotRepository,
                calendarRepository,
                userRepository,
                mapper,
                meterRegistry
        );
    }

    @Test
    void scheduleMeeting_whenSlotNotFree_throwsConflict() {
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

        ScheduleMeetingRequest request = new ScheduleMeetingRequest(slotId, "Team Sync", "desc", Set.of());

        assertThatThrownBy(() -> service.scheduleMeeting(userId, request))
                .isInstanceOf(SlotConflictException.class)
                .hasMessageContaining("already busy");

        verify(meetingRepository, never()).save(any(Meeting.class));
    }

    @Test
    void scheduleMeeting_whenMeetingAlreadyExists_throwsConflict() {
        UUID userId = UUID.randomUUID();
        UUID calendarId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        Calendar calendar = new Calendar();
        calendar.setId(calendarId);
        calendar.setUserId(userId);

        TimeSlot freeSlot = new TimeSlot();
        freeSlot.setId(slotId);
        freeSlot.setCalendarId(calendarId);
        freeSlot.setStatus(SlotStatus.FREE);

        when(calendarRepository.findByUserId(userId)).thenReturn(Optional.of(calendar));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(freeSlot));
        when(meetingRepository.existsBySlotId(slotId)).thenReturn(true);

        ScheduleMeetingRequest request = new ScheduleMeetingRequest(slotId, "Team Sync", "desc", Set.of());

        assertThatThrownBy(() -> service.scheduleMeeting(userId, request))
                .isInstanceOf(SlotConflictException.class)
                .hasMessageContaining("already converted");

        verify(meetingRepository, never()).save(any(Meeting.class));
    }

    @Test
    void cancelMeeting_revertsSlotStatusToFreeAndDeletesMeeting() {
        UUID userId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        Meeting meeting = new Meeting();
        meeting.setId(meetingId);
        meeting.setOrganizerId(userId);
        meeting.setSlotId(slotId);
        meeting.setParticipants(Set.of());

        TimeSlot slot = new TimeSlot();
        slot.setId(slotId);
        slot.setStatus(SlotStatus.BUSY);

        when(meetingRepository.findByIdWithParticipants(meetingId)).thenReturn(Optional.of(meeting));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        service.cancelMeeting(userId, meetingId);

        assertThat(slot.getStatus()).isEqualTo(SlotStatus.FREE);
        verify(slotRepository).save(eq(slot));
        verify(meetingRepository).delete(eq(meeting));
    }

    @Test
    void scheduleMeeting_onSuccess_setsSlotToBusyAndIncrementsCounter() {
        UUID userId = UUID.randomUUID();
        UUID calendarId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        Calendar calendar = new Calendar();
        calendar.setId(calendarId);
        calendar.setUserId(userId);

        TimeSlot freeSlot = new TimeSlot();
        freeSlot.setId(slotId);
        freeSlot.setCalendarId(calendarId);
        freeSlot.setStatus(SlotStatus.FREE);

        when(calendarRepository.findByUserId(userId)).thenReturn(Optional.of(calendar));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(freeSlot));
        when(meetingRepository.existsBySlotId(slotId)).thenReturn(false);
        when(slotRepository.saveAndFlush(any(TimeSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleMeetingRequest request = new ScheduleMeetingRequest(slotId, "Planning", "desc", Set.of());
        MeetingResponse response = service.scheduleMeeting(userId, request);

        assertThat(response.slotId()).isEqualTo(slotId);
        assertThat(freeSlot.getStatus()).isEqualTo(SlotStatus.BUSY);
        verify(slotRepository).saveAndFlush(eq(freeSlot));
        verify(meetingsScheduledCounter).increment();
    }
}
