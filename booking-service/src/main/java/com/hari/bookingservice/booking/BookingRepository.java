package com.hari.bookingservice.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findBySourceHoldId(UUID sourceHoldId);

    List<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByEventIdAndStatus(UUID eventId, BookingStatus status);
}