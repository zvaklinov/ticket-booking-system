package com.hari.bookingservice.seat;

import com.hari.bookingservice.event.EventServiceClient;
import com.hari.bookingservice.event.EventSummary;
import com.hari.bookingservice.seat.dto.CreateSeatRequest;
import com.hari.bookingservice.seat.dto.SeatResponse;
import com.hari.bookingservice.seat.exceptions.SeatCreationNotAllowedException;
import com.hari.bookingservice.seat.exceptions.SeatNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SeatService {

    private final SeatRepository seatRepository;
    private final EventServiceClient eventServiceClient;

    public SeatService(SeatRepository seatRepository, EventServiceClient eventServiceClient) {
        this.seatRepository = seatRepository;
        this.eventServiceClient = eventServiceClient;
    }

    @Transactional
    public SeatResponse create(CreateSeatRequest request) {
        // Fail closed: if Event Service is unreachable, EventServiceUnavailableException
        // propagates 503, and no seat is created.
        EventSummary event = eventServiceClient.getEvent(request.eventId());

        if ("CANCELLED".equals(event.status()) || "ARCHIVED".equals(event.status())) {
            throw new SeatCreationNotAllowedException(request.eventId(), event.status());
        }

        Seat seat = new Seat(
                request.eventId(),
                request.seatLabel(),
                request.price(),
                request.currency()
        );

        return SeatResponse.from(seatRepository.saveAndFlush(seat));
    }

    @Transactional
    public SeatResponse markUnavailable(UUID seatId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new SeatNotFoundException(seatId));
        seat.markUnavailable();
        return SeatResponse.from(seatRepository.saveAndFlush(seat));
    }

    @Transactional
    public SeatResponse markAvailable(UUID seatId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new SeatNotFoundException(seatId));
        seat.markAvailable();
        return SeatResponse.from(seatRepository.saveAndFlush(seat));
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> listByEvent(UUID eventId) {
        return seatRepository.findByEventIdOrderBySeatLabelAsc(eventId)
                .stream()
                .map(SeatResponse::from)
                .toList();
    }
}