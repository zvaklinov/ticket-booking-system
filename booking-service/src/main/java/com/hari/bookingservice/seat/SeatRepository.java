package com.hari.bookingservice.seat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByEventIdOrderBySeatLabelAsc(UUID eventId);

    boolean existsByEventIdAndSeatLabel(UUID eventId, String seatLabel);

    /**
     * Atomically claims seats: the WHERE clause is both the check and the set, so there is no
     * window between testing status and writing it. Returns the number of rows actually updated —
     * if that is less than the number of seats requested, at least one seat was not AVAILABLE and
     * the caller must roll back the whole transaction (no partial holds).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE seat
            SET status = 'HELD',
                active_hold_id = :holdId,
                hold_expires_at = :expiresAt,
                version = version + 1,
                updated_at = now()
            WHERE event_id = :eventId
              AND id IN (:seatIds)
              AND status = 'AVAILABLE'
            """, nativeQuery = true)
    int claimSeats(@Param("eventId") UUID eventId,
                   @Param("seatIds") Collection<UUID> seatIds,
                   @Param("holdId") UUID holdId,
                   @Param("expiresAt") Instant expiresAt);

    /** Identifies which of the requested seats were NOT claimed by this hold, for the error response. */
    @Query(value = """
            SELECT seat_label
            FROM seat
            WHERE id IN (:seatIds)
              AND (active_hold_id IS NULL OR active_hold_id <> :holdId)
            ORDER BY seat_label
            """, nativeQuery = true)
    List<String> findLabelsNotClaimedBy(@Param("seatIds") Collection<UUID> seatIds,
                                        @Param("holdId") UUID holdId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE seat
            SET status = 'AVAILABLE',
                active_hold_id = NULL,
                hold_expires_at = NULL,
                version = version + 1,
                updated_at = now()
            WHERE active_hold_id = :holdId
              AND status = 'HELD'
            """, nativeQuery = true)
    int releaseSeatsForHold(@Param("holdId") UUID holdId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE seat
        SET status = 'BOOKED',
            active_hold_id = NULL,
            hold_expires_at = NULL,
            version = version + 1,
            updated_at = now()
        WHERE active_hold_id = :holdId
          AND status = 'HELD'
        """, nativeQuery = true)
    int bookSeatsForHold(@Param("holdId") UUID holdId);
}
