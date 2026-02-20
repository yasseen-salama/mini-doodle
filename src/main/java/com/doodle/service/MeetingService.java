package com.doodle.service;

import com.doodle.domain.Calendar;
import com.doodle.domain.Meeting;
import com.doodle.domain.SlotStatus;
import com.doodle.domain.TimeSlot;
import com.doodle.domain.User;
import com.doodle.dto.request.ScheduleMeetingRequest;
import com.doodle.dto.request.UpdateMeetingRequest;
import com.doodle.dto.response.MeetingResponse;
import com.doodle.exception.ResourceNotFoundException;
import com.doodle.exception.SlotConflictException;
import com.doodle.mapper.MeetingMapper;
import com.doodle.repository.CalendarRepository;
import com.doodle.repository.MeetingRepository;
import com.doodle.repository.TimeSlotRepository;
import com.doodle.repository.UserRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final TimeSlotRepository slotRepository;
    private final CalendarRepository calendarRepository;
    private final UserRepository userRepository;
    private final MeetingMapper mapper;

    public MeetingService(
            MeetingRepository meetingRepository,
            TimeSlotRepository slotRepository,
            CalendarRepository calendarRepository,
            UserRepository userRepository,
            MeetingMapper mapper
    ) {
        this.meetingRepository = meetingRepository;
        this.slotRepository = slotRepository;
        this.calendarRepository = calendarRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    @Transactional
    public MeetingResponse scheduleMeeting(UUID userId, ScheduleMeetingRequest req) {
        TimeSlot slot = getSlotWithOwnershipCheck(userId, req.slotId());

        if (slot.getStatus() != SlotStatus.FREE) {
            throw new SlotConflictException("Slot is already busy");
        }

        if (meetingRepository.existsBySlotId(req.slotId())) {
            throw new SlotConflictException("Slot already converted to a meeting");
        }

        Set<User> participants = resolveParticipants(req.participantIds());

        Meeting meeting = new Meeting();
        meeting.setId(UUID.randomUUID());
        meeting.setSlotId(req.slotId());
        meeting.setOrganizerId(userId);
        meeting.setTitle(req.title().trim());
        meeting.setDescription(req.description());
        meeting.setParticipants(participants);

        slot.setStatus(SlotStatus.BUSY);
        slotRepository.save(slot);

        return mapper.toResponse(meetingRepository.save(meeting));
    }

    public Page<MeetingResponse> getMeetings(UUID userId, Instant from, Instant to, Pageable pageable) {
        validateWindow(from, to);
        return meetingRepository.findMyMeetingsInRange(userId, from, to, pageable)
                .map(mapper::toResponse);
    }

    public MeetingResponse getMeeting(UUID userId, UUID meetingId) {
        return mapper.toResponse(getMeetingWithAccessCheck(userId, meetingId));
    }

    @Transactional
    public MeetingResponse updateMeeting(UUID userId, UUID meetingId, UpdateMeetingRequest req) {
        Meeting meeting = getMeetingWithOwnershipCheck(userId, meetingId);

        if (req.title() != null) {
            String title = req.title().trim();
            if (title.isEmpty()) {
                throw new IllegalArgumentException("title must not be blank");
            }
            meeting.setTitle(title);
        }

        if (req.description() != null) {
            meeting.setDescription(req.description());
        }

        if (req.participantIds() != null) {
            meeting.setParticipants(resolveParticipants(req.participantIds()));
        }

        return mapper.toResponse(meetingRepository.save(meeting));
    }

    @Transactional
    public void cancelMeeting(UUID userId, UUID meetingId) {
        Meeting meeting = getMeetingWithOwnershipCheck(userId, meetingId);
        TimeSlot slot = slotRepository.findById(meeting.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot for meeting not found"));

        slot.setStatus(SlotStatus.FREE);
        slotRepository.save(slot);
        meetingRepository.delete(meeting);
    }

    private TimeSlot getSlotWithOwnershipCheck(UUID userId, UUID slotId) {
        Calendar calendar = calendarRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar not found for user"));
        TimeSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Time slot not found"));
        if (!slot.getCalendarId().equals(calendar.getId())) {
            throw new ResourceNotFoundException("Time slot not found");
        }
        return slot;
    }

    private Meeting getMeetingWithAccessCheck(UUID userId, UUID meetingId) {
        Meeting meeting = meetingRepository.findByIdWithParticipants(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));
        boolean isOrganizer = meeting.getOrganizerId().equals(userId);
        boolean isParticipant = meeting.getParticipants().stream()
                .anyMatch(user -> user.getId().equals(userId));
        if (!isOrganizer && !isParticipant) {
            throw new ResourceNotFoundException("Meeting not found");
        }
        return meeting;
    }

    private Meeting getMeetingWithOwnershipCheck(UUID userId, UUID meetingId) {
        Meeting meeting = meetingRepository.findByIdWithParticipants(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));
        if (!meeting.getOrganizerId().equals(userId)) {
            throw new ResourceNotFoundException("Meeting not found");
        }
        return meeting;
    }

    private Set<User> resolveParticipants(Set<UUID> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) {
            return new HashSet<>();
        }

        Set<UUID> uniqueIds = new HashSet<>(participantIds);
        List<User> users = userRepository.findAllById(uniqueIds);
        if (users.size() != uniqueIds.size()) {
            Set<UUID> foundIds = users.stream().map(User::getId).collect(Collectors.toSet());
            Set<UUID> missing = new HashSet<>(uniqueIds);
            missing.removeAll(foundIds);
            throw new ResourceNotFoundException("Participants not found: " + missing);
        }
        return new HashSet<>(users);
    }

    private void validateWindow(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("to must be after from");
        }
    }
}
