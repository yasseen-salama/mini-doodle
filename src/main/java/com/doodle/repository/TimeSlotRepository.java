package com.doodle.repository;

import com.doodle.domain.SlotStatus;
import com.doodle.domain.TimeSlot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, UUID> {

    @Query("SELECT ts FROM TimeSlot ts " +
            "WHERE ts.calendarId = :calendarId " +
            "AND ts.startTime < :endTime " +
            "AND ts.endTime > :startTime " +
            "ORDER BY ts.startTime")
    Page<TimeSlot> findByCalendarAndRange(
            @Param("calendarId") UUID calendarId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            Pageable pageable
    );

    @Query("SELECT COUNT(ts) > 0 FROM TimeSlot ts " +
            "WHERE ts.calendarId = :calendarId " +
            "AND (:excludeId IS NULL OR ts.id != :excludeId) " +
            "AND ts.startTime < :endTime " +
            "AND ts.endTime > :startTime")
    boolean existsOverlapping(
            @Param("calendarId") UUID calendarId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("excludeId") UUID excludeId
    );

    @Query("SELECT ts FROM TimeSlot ts " +
            "WHERE ts.calendarId = :calendarId " +
            "AND ts.startTime < :endTime " +
            "AND ts.endTime > :startTime " +
            "AND ts.status = :status " +
            "ORDER BY ts.startTime")
    List<TimeSlot> findByCalendarRangeAndStatus(
            @Param("calendarId") UUID calendarId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("status") SlotStatus status
    );
}
