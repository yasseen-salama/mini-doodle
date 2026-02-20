package com.doodle.controller;

import com.doodle.dto.response.AvailabilityResponse;
import com.doodle.service.AvailabilityService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public AvailabilityResponse getAvailability(
            @RequestParam("userId") UUID userId,
            @RequestParam("from") Instant from,
            @RequestParam("to") Instant to
    ) {
        return availabilityService.getAvailability(userId, from, to);
    }
}
