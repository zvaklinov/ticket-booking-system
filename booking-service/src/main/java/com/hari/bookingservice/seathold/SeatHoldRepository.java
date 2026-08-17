package com.hari.bookingservice.seathold;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SeatHoldRepository extends JpaRepository<SeatHold, UUID> {

    boolean existsByUserIdAndEventIdAndStatusIn(UUID userId, UUID eventId, List<SeatHoldStatus> statuses);

    List<SeatHold> findByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            SeatHoldStatus status, Instant now, Pageable pageable);

    List<SeatHold> findByStatusAndPaymentDeadlineAtBeforeOrderByPaymentDeadlineAtAsc(
            SeatHoldStatus status, Instant now, Pageable pageable);

    List<SeatHold> findByEventIdAndStatusIn(
            UUID eventId, List<SeatHoldStatus> statuses, Pageable pageable);
}
