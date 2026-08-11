package com.hari.bookingservice.seathold;

import com.hari.bookingservice.common.IdempotencyKey;
import com.hari.bookingservice.common.IdempotencyKeyRepository;
import com.hari.bookingservice.event.EventBookability;
import com.hari.bookingservice.event.EventBookabilityRepository;
import com.hari.bookingservice.seat.Seat;
import com.hari.bookingservice.seat.SeatRepository;
import com.hari.bookingservice.seathold.dto.CreateSeatHoldRequest;
import com.hari.bookingservice.seathold.dto.SeatHoldResponse;
import com.hari.bookingservice.seathold.exceptions.ActiveHoldAlreadyExistsException;
import com.hari.bookingservice.seathold.exceptions.EventNotBookableException;
import com.hari.bookingservice.seathold.exceptions.IdempotencyKeyConflictException;
import com.hari.bookingservice.seathold.exceptions.SeatsNotAvailableException;
import com.hari.bookingservice.seathold.exceptions.SeatHoldNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
class SeatHoldCreator {

    private final SeatRepository seatRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final SeatHoldItemRepository seatHoldItemRepository;
    private final EventBookabilityRepository eventBookabilityRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final Clock clock;
    private final Duration holdDuration;

    SeatHoldCreator(SeatRepository seatRepository,
                    SeatHoldRepository seatHoldRepository,
                    SeatHoldItemRepository seatHoldItemRepository,
                    EventBookabilityRepository eventBookabilityRepository,
                    IdempotencyKeyRepository idempotencyKeyRepository,
                    Clock clock,
                    @Value("${booking.hold.duration}") Duration holdDuration) {
        this.seatRepository = seatRepository;
        this.seatHoldRepository = seatHoldRepository;
        this.seatHoldItemRepository = seatHoldItemRepository;
        this.eventBookabilityRepository = eventBookabilityRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.clock = clock;
        this.holdDuration = holdDuration;
    }

    @Transactional
    SeatHoldResponse createInternal(CreateSeatHoldRequest request, String idempotencyKey, String requestHash) {
        Instant now = clock.instant();

        Optional<IdempotencyKey> existing =
                idempotencyKeyRepository.findByUserIdAndIdempotencyKey(request.userId(), idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyKey key = existing.get();
            if (!key.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyConflictException(idempotencyKey);
            }
            return loadResponse(key.getResourceId());
        }

        EventBookability bookability = eventBookabilityRepository.findById(request.eventId())
                .orElseThrow(() -> new EventNotBookableException(request.eventId(),
                        "event is not known to Booking Service"));
        if (!bookability.isBookableAt(now)) {
            throw new EventNotBookableException(request.eventId(),
                    "status=" + bookability.getStatus() + ", booking window "
                            + bookability.getBookingOpensAtUtc() + " .. " + bookability.getBookingClosesAtUtc());
        }

        boolean hasActiveHold = seatHoldRepository.existsByUserIdAndEventIdAndStatusIn(
                request.userId(), request.eventId(),
                List.of(SeatHoldStatus.ACTIVE, SeatHoldStatus.PAYMENT_PENDING));
        if (hasActiveHold) {
            throw new ActiveHoldAlreadyExistsException(request.userId(), request.eventId());
        }

        List<UUID> seatIds = request.seatIds().stream().distinct().sorted().toList();

        List<Seat> seats = seatRepository.findAllById(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new EventNotBookableException(request.eventId(), "one or more seats do not exist");
        }
        if (!seats.stream().allMatch(s -> s.getEventId().equals(request.eventId()))) {
            throw new EventNotBookableException(request.eventId(),
                    "one or more seats belong to a different event");
        }

        BigDecimal totalAmount = seats.stream()
                .map(Seat::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String currency = seats.get(0).getCurrency();

        Instant expiresAt = now.plus(holdDuration);
        SeatHold hold = seatHoldRepository.saveAndFlush(
                new SeatHold(request.eventId(), request.userId(), expiresAt, totalAmount, currency));

        int rowsClaimed = seatRepository.claimSeats(request.eventId(), seatIds, hold.getId(), expiresAt);
        if (rowsClaimed != seatIds.size()) {
            List<String> unavailable = seatRepository.findLabelsNotClaimedBy(seatIds, hold.getId());
            throw new SeatsNotAvailableException(unavailable);
        }

        Map<UUID, Seat> seatsById = seats.stream()
                .collect(Collectors.toMap(Seat::getId, Function.identity()));
        List<SeatHoldItem> items = seatIds.stream()
                .map(seatId -> {
                    Seat seat = seatsById.get(seatId);
                    return new SeatHoldItem(hold.getId(), seatId, seat.getPrice(), seat.getCurrency());
                })
                .toList();
        seatHoldItemRepository.saveAll(items);

        idempotencyKeyRepository.save(
                new IdempotencyKey(request.userId(), idempotencyKey, requestHash, hold.getId()));

        return SeatHoldResponse.from(hold, items);
    }

    /**
     * Runs in its own transaction so it can be called after createInternal's transaction has
     * rolled back. Returns the hold created by a concurrent request using the same key, if any.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    Optional<SeatHoldResponse> findExistingForKey(UUID userId, String idempotencyKey, String requestHash) {
        return idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .filter(key -> key.getRequestHash().equals(requestHash))
                .map(key -> loadResponse(key.getResourceId()));
    }

    @Transactional(readOnly = true)
    SeatHoldResponse loadResponse(UUID holdId) {
        SeatHold hold = seatHoldRepository.findById(holdId)
                .orElseThrow(() -> new SeatHoldNotFoundException(holdId));
        return SeatHoldResponse.from(hold, seatHoldItemRepository.findBySeatHoldId(holdId));
    }
}