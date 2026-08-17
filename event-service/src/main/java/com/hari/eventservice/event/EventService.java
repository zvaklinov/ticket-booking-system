package com.hari.eventservice.event;

import com.hari.eventservice.category.Category;
import com.hari.eventservice.category.CategoryRepository;
import com.hari.eventservice.common.EntityNotFoundException;
import com.hari.eventservice.event.dto.EventResponse;
import com.hari.eventservice.outbox.OutboxWriter;
import com.hari.eventservice.outbox.events.EventArchivedPayload;
import com.hari.eventservice.outbox.events.EventCancelledPayload;
import com.hari.eventservice.outbox.events.EventPublishedPayload;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final OutboxWriter outboxWriter;
    private final Clock clock;

    public EventService(EventRepository eventRepository,
                        CategoryRepository categoryRepository,
                        OutboxWriter outboxWriter,
                        Clock clock) {
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    @Transactional
    public EventResponse create(UUID categoryId, String title, String description, String location, String venueName,
                                String venueTimezone,  Instant startTimeUtc, Instant endTimeUtc, Instant bookingOpensAtUtc,
                                Instant bookingClosesAtUtc, String currency) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + categoryId));

        Event event = new Event(category, title, description, location, venueName, venueTimezone, startTimeUtc, endTimeUtc,
                bookingOpensAtUtc, bookingClosesAtUtc, currency);

        return EventResponse.from(eventRepository.saveAndFlush(event));
    }

    @Transactional(readOnly = true)
    public EventResponse getById(UUID eventId) {
        return EventResponse.from(findOrThrow(eventId));
    }

    @Transactional
    public EventResponse publish(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event: " + eventId));
        event.publish();
        outboxWriter.write("EventPublished", event.getId(), toPublishedPayload(event));
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse cancel(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event: " + eventId));
        event.cancel();
        outboxWriter.write("EventCancelled", event.getId(), toCancelledPayload(event));
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse archive(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event: " + eventId));
        event.archive(clock.instant());
        outboxWriter.write("EventArchived", event.getId(), toArchivedPayload(event));
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> search(UUID categoryId, String location, Instant dateFrom, Instant dateTo,
                              BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        Specification<Event> spec = EventSearchSpecification.build(
                categoryId, location, dateFrom, dateTo, minPrice, maxPrice);
        return eventRepository.findAll(spec, pageable).map(EventResponse::from);
    }

    private Event findOrThrow(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));
    }

    private EventPublishedPayload toPublishedPayload(Event event) {
        return new EventPublishedPayload(
                event.getId(),
                event.getTitle(),
                event.getCategory().getId(),
                event.getLocation(),
                event.getVenueTimezone(),
                event.getStartTimeUtc(),
                event.getEndTimeUtc(),
                event.getBookingOpensAtUtc(),
                event.getBookingClosesAtUtc(),
                event.getCurrency(),
                event.getStatus().name());
    }

    private EventCancelledPayload toCancelledPayload(Event event) {
        return new EventCancelledPayload(event.getId(), event.getStatus().name());
    }

    private EventArchivedPayload toArchivedPayload(Event event) {
        return new EventArchivedPayload(event.getId(), event.getStatus().name());
    }
}
