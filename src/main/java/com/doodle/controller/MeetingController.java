package com.doodle.controller;

import com.doodle.dto.request.ScheduleMeetingRequest;
import com.doodle.dto.request.UpdateMeetingRequest;
import com.doodle.dto.response.MeetingResponse;
import com.doodle.service.CurrentUserService;
import com.doodle.service.MeetingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/meetings")
public class MeetingController {

    private final MeetingService meetingService;
    private final CurrentUserService currentUserService;

    public MeetingController(MeetingService meetingService, CurrentUserService currentUserService) {
        this.meetingService = meetingService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<MeetingResponse> scheduleMeeting(
            Authentication authentication,
            @Valid @RequestBody ScheduleMeetingRequest request
    ) {
        UUID userId = currentUserService.resolveUserId(authentication.getName());
        MeetingResponse response = meetingService.scheduleMeeting(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public Page<MeetingResponse> getMeetings(
            Authentication authentication,
            @RequestParam("from") Instant from,
            @RequestParam("to") Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        UUID userId = currentUserService.resolveUserId(authentication.getName());
        return meetingService.getMeetings(userId, from, to, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public MeetingResponse getMeeting(Authentication authentication, @PathVariable UUID id) {
        UUID userId = currentUserService.resolveUserId(authentication.getName());
        return meetingService.getMeeting(userId, id);
    }

    @PatchMapping("/{id}")
    public MeetingResponse updateMeeting(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMeetingRequest request
    ) {
        UUID userId = currentUserService.resolveUserId(authentication.getName());
        return meetingService.updateMeeting(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelMeeting(Authentication authentication, @PathVariable UUID id) {
        UUID userId = currentUserService.resolveUserId(authentication.getName());
        meetingService.cancelMeeting(userId, id);
    }
}
