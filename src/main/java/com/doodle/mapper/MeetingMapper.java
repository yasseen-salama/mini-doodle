package com.doodle.mapper;

import com.doodle.domain.Meeting;
import com.doodle.dto.response.MeetingResponse;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MeetingMapper {

    public MeetingResponse toResponse(Meeting meeting) {
        Set<UUID> participantIds = meeting.getParticipants().stream()
                .map(user -> user.getId())
                .collect(Collectors.toSet());

        return new MeetingResponse(
                meeting.getId(),
                meeting.getSlotId(),
                meeting.getOrganizerId(),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getCreatedAt(),
                participantIds
        );
    }
}
