package com.doodle.repository;

import com.doodle.domain.Calendar;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarRepository extends JpaRepository<Calendar, UUID> {

    Optional<Calendar> findByUserId(UUID userId);
}
