package com.hari.bookingservice.seat;

import com.hari.bookingservice.event.EventServiceClient;
import com.hari.bookingservice.event.EventSummary;
import com.hari.bookingservice.outbox.OutboxWriter;
import com.hari.bookingservice.outbox.events.EventPriceTiersChangedPayload;
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
    private final OutboxWriter outboxWriter;

    public SeatService(SeatRepository seatRepository, EventServiceClient eventServiceClient, OutboxWriter outboxWriter) {
        this.seatRepository = seatRepository;
        this.eventServiceClient = eventServiceClient;
        this.outboxWriter = outboxWriter;
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

        Seat saved = seatRepository.saveAndFlush(seat);

        List<EventPriceTiersChangedPayload.PriceTier> tiers =
                seatRepository.findDistinctPriceTiers(request.eventId()).stream()
                        .map(view -> new EventPriceTiersChangedPayload.PriceTier(
                                view.getPrice(), view.getCurrency()))
                        .toList();

        outboxWriter.write("EventPriceTiersChanged", request.eventId(),
                new EventPriceTiersChangedPayload(request.eventId(), tiers));

        return SeatResponse.from(saved);
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