package com.hari.bookingservice.seathold;

import com.hari.bookingservice.seat.SeatRepository;
import com.hari.bookingservice.seathold.dto.CreateSeatHoldRequest;
import com.hari.bookingservice.seathold.dto.SeatHoldResponse;
import com.hari.bookingservice.seathold.exceptions.ActiveHoldAlreadyExistsException;
import com.hari.bookingservice.seathold.exceptions.SeatHoldAccessDeniedException;
import com.hari.bookingservice.seathold.exceptions.SeatHoldNotFoundException;
import com.hari.bookingservice.seathold.exceptions.SeatsNotAvailableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SeatHoldService {

    private final SeatHoldCreator creator;
    private final SeatHoldRepository seatHoldRepository;
    private final SeatRepository seatRepository;
    private final Clock clock;

    public SeatHoldService(SeatHoldCreator creator,
                           SeatHoldRepository seatHoldRepository,
                           SeatRepository seatRepository,
                           Clock clock) {
        this.creator = creator;
        this.seatHoldRepository = seatHoldRepository;
        this.seatRepository = seatRepository;
        this.clock = clock;
    }

    /**
     * Deliberately NOT @Transactional. A concurrent request using the same idempotency key can
     * cause this attempt to fail — either on the seat claim or on the idempotency key insert —
     * and recovering requires reading data committed by that other request, which is impossible
     * inside the transaction that just rolled back.
     */
    public SeatHoldResponse create(CreateSeatHoldRequest request, UUID userId, String idempotencyKey) {
        String requestHash = hashRequest(request, userId);
        try {
            return creator.createInternal(request, userId, idempotencyKey, requestHash);
        } catch (SeatsNotAvailableException | ActiveHoldAlreadyExistsException
                 | DataIntegrityViolationException e) {
            // These are the failures a same-key concurrent winner can cause. If that winner
            // committed a hold for this exact request, this call is a duplicate and must return
            // the same result. Otherwise, the failure is real and propagates unchanged.
            return creator.findExistingForKey(userId, idempotencyKey, requestHash)
                    .orElseThrow(() -> e);
        }
    }

    @Transactional
    public void release(UUID holdId, UUID userId) {
        SeatHold hold = seatHoldRepository.findById(holdId)
                .orElseThrow(() -> new SeatHoldNotFoundException(holdId));

        if (!hold.getUserId().equals(userId)) {
            throw new SeatHoldAccessDeniedException(holdId);
        }
        if (hold.getStatus() == SeatHoldStatus.RELEASED) {
            return;
        }

        hold.release(clock.instant());
        seatRepository.releaseSeatsForHold(holdId);
    }

    @Transactional(readOnly = true)
    public SeatHoldResponse getById(UUID holdId, UUID userId) {
        SeatHold hold = seatHoldRepository.findById(holdId)
                .orElseThrow(() -> new SeatHoldNotFoundException(holdId));

        if (!hold.getUserId().equals(userId)) {
            throw new SeatHoldAccessDeniedException(holdId);
        }

        return creator.loadResponse(holdId);
    }

    private String hashRequest(CreateSeatHoldRequest request, UUID userId) {
        String canonical = request.eventId() + "|" + userId + "|"
                + request.seatIds().stream().distinct().sorted()
                .map(UUID::toString).collect(Collectors.joining(","));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}