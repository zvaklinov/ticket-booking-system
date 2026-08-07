package event_service.event_service.event.dto;

import event_service.event_service.event.Event;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String title,
        String description,
        UUID categoryId,
        String categoryName,
        String location,
        String venueName,
        String venueTimezone,
        Instant startTimeUtc,
        Instant endTimeUtc,
        Instant bookingOpensAtUtc,
        Instant bookingClosesAtUtc,
        String currency,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(), event.getTitle(), event.getDescription(),
                event.getCategory().getId(), event.getCategory().getName(),
                event.getLocation(), event.getVenueName(), event.getVenueTimezone(),
                event.getStartTimeUtc(), event.getEndTimeUtc(),
                event.getBookingOpensAtUtc(), event.getBookingClosesAtUtc(),
                event.getCurrency(), event.getStatus().name(),
                event.getCreatedAt(), event.getUpdatedAt()
        );
    }
}