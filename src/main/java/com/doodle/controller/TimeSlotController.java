package com.doodle.controller;

import com.doodle.dto.request.CreateSlotRequest;
import com.doodle.dto.request.UpdateSlotRequest;
import com.doodle.dto.response.TimeSlotResponse;
import com.doodle.service.CurrentUserService;
import com.doodle.service.TimeSlotService;
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
@RequestMapping("/api/slots")
public class TimeSlotController {

    private final TimeSlotService timeSlotService;
    private final CurrentUserService currentUserService;

    public TimeSlotController(TimeSlotService timeSlotService, CurrentUserService currentUserService) {
        this.timeSlotService = timeSlotService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<TimeSlotResponse> createSlot(
            Authentication authentication,
            @Valid @RequestBody CreateSlotRequest request
    ) {
        UUID userId = currentUserService.resolveUserId(authentication.getName());
        TimeSlotResponse response = timeSlotService.createSlot(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public Page<TimeSlotResponse> getSlotsInRange(
            Authentication authentication,
            @RequestParam("from") Instant from,
            @RequestParam("to") Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        UUID userId = currentUserService.resolveUserId(authentication.getName());
        return timeSlotService.getSlotsInRange(userId, from, to, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public TimeSlotResponse getSlot(Authentication authentication, @PathVariable UUID id) {
        UUID userId = currentUserService.resolveUserId(authentication.getName());
        return timeSlotService.getSlot(userId, id);
    }

    @PatchMapping("/{id}")
    public TimeSlotResponse updateSlot(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSlotRequest request
    ) {
        UUID userId = currentUserService.resolveUserId(authentication.getName());
        return timeSlotService.updateSlot(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSlot(Authentication authentication, @PathVariable UUID id) {
        UUID userId = currentUserService.resolveUserId(authentication.getName());
        timeSlotService.deleteSlot(userId, id);
    }
}
