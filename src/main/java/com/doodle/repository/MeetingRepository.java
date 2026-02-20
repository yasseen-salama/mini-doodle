package com.doodle.repository;

import com.doodle.domain.Meeting;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    boolean existsBySlotId(UUID slotId);

    @EntityGraph(attributePaths = "participants")
    @Query("SELECT m FROM Meeting m WHERE m.id = :meetingId")
    Optional<Meeting> findByIdWithParticipants(@Param("meetingId") UUID meetingId);

    @Query(
            value = "SELECT m FROM Meeting m " +
                    "JOIN TimeSlot ts ON ts.id = m.slotId " +
                    "WHERE (m.organizerId = :userId OR EXISTS (" +
                    "   SELECT 1 FROM m.participants p WHERE p.id = :userId" +
                    ")) " +
                    "AND ts.startTime < :to " +
                    "AND ts.endTime > :from " +
                    "ORDER BY ts.startTime",
            countQuery = "SELECT COUNT(m) FROM Meeting m " +
                    "JOIN TimeSlot ts ON ts.id = m.slotId " +
                    "WHERE (m.organizerId = :userId OR EXISTS (" +
                    "   SELECT 1 FROM m.participants p WHERE p.id = :userId" +
                    ")) " +
                    "AND ts.startTime < :to " +
                    "AND ts.endTime > :from"
    )
    Page<Meeting> findMyMeetingsInRange(
            @Param("userId") UUID userId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );
}
