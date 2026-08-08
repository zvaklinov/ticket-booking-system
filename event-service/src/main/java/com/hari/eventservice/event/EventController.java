package com.hari.eventservice.event;

import com.hari.eventservice.event.dto.CreateEventRequest;
import com.hari.eventservice.event.dto.EventResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        EventResponse response = eventService.create(
                request.categoryId(), request.title(), request.startTimeUtc(), request.endTimeUtc(),
                request.bookingOpensAtUtc(), request.bookingClosesAtUtc(), request.currency());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{eventId}")
    public EventResponse getById(@PathVariable UUID eventId) {
        return eventService.getById(eventId);
    }

    @GetMapping
    public Page<EventResponse> search(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Pageable pageable) {
        return eventService.search(categoryId, location, dateFrom, dateTo, minPrice, maxPrice, pageable);
    }

    @PostMapping("/{eventId}/publish")
    public ResponseEntity<Void> publish(@PathVariable UUID eventId) {
        eventService.publish(eventId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{eventId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID eventId) {
        eventService.cancel(eventId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{eventId}/archive")
    public ResponseEntity<Void> archive(@PathVariable UUID eventId) {
        eventService.archive(eventId);
        return ResponseEntity.ok().build();
    }
}