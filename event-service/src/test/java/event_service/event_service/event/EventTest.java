package event_service.event_service.event;

import event_service.event_service.common.InvalidEventTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    // --- publish() ---

    @Test
    void publish_fromDraft_transitionsToPublished() {
        Event event = EventTestFixtures.publishableDraftEvent();
        event.publish();
        assertEquals(EventStatus.PUBLISHED, event.getStatus());
    }

    @Test
    void publish_alreadyPublished_isIdempotentNoOp() {
        Event event = EventTestFixtures.publishableDraftEvent();
        event.publish();

        assertDoesNotThrow(event::publish);
        assertEquals(EventStatus.PUBLISHED, event.getStatus());
    }

    @Test
    void publish_fromCancelled_throws() {
        Event event = EventTestFixtures.publishableDraftEvent();
        event.publish();
        event.cancel();

        assertThrows(InvalidEventTransitionException.class, event::publish);
    }

    @Test
    void publish_fromArchived_throws() {
        Event event = EventTestFixtures.draftEvent();
        event.archive(Instant.now());

        assertThrows(InvalidEventTransitionException.class, event::publish);
    }

    @Test
    void publish_missingVenue_throws() {
        Event event = EventTestFixtures.draftEvent(); // no venue set

        assertThrows(InvalidEventTransitionException.class, event::publish);
    }

    @Test
    void publish_bookingWindowOutOfOrder_throws() {
        Event event = EventTestFixtures.publishableDraftWithBadBookingWindow();

        assertThrows(InvalidEventTransitionException.class, event::publish);
    }

    // --- cancel() ---

    @Test
    void cancel_fromPublished_transitionsToCancelled() {
        Event event = EventTestFixtures.publishableDraftEvent();
        event.publish();

        event.cancel();

        assertEquals(EventStatus.CANCELLED, event.getStatus());
    }

    @Test
    void cancel_alreadyCancelled_isIdempotentNoOp() {
        Event event = EventTestFixtures.publishableDraftEvent();
        event.publish();
        event.cancel();

        assertDoesNotThrow(event::cancel);
        assertEquals(EventStatus.CANCELLED, event.getStatus());
    }

    @Test
    void cancel_fromDraft_throws() {
        Event event = EventTestFixtures.draftEvent();

        assertThrows(InvalidEventTransitionException.class, event::cancel);
    }

    @Test
    void cancel_fromArchived_throws() {
        Event event = EventTestFixtures.draftEvent();
        event.archive(Instant.now());

        assertThrows(InvalidEventTransitionException.class, event::cancel);
    }

    // --- archive() ---

    @Test
    void archive_fromDraft_transitionsToArchived() {
        Event event = EventTestFixtures.draftEvent();

        event.archive(Instant.now());

        assertEquals(EventStatus.ARCHIVED, event.getStatus());
    }

    @Test
    void archive_fromCancelled_transitionsToArchived() {
        Event event = EventTestFixtures.publishableDraftEvent();
        event.publish();
        event.cancel();

        event.archive(Instant.now());

        assertEquals(EventStatus.ARCHIVED, event.getStatus());
    }

    @Test
    void archive_fromPublished_whenConcluded_transitionsToArchived() {
        Event event = EventTestFixtures.publishableDraftEvent();
        event.publish();

        Instant wellAfterEnd = event.getEndTimeUtc().plus(1, ChronoUnit.DAYS);
        event.archive(wellAfterEnd);

        assertEquals(EventStatus.ARCHIVED, event.getStatus());
    }

    @Test
    void archive_fromPublished_whenNotConcluded_throws() {
        Event event = EventTestFixtures.publishableDraftEvent();
        event.publish();

        Instant beforeEnd = event.getEndTimeUtc().minus(1, ChronoUnit.HOURS);

        assertThrows(InvalidEventTransitionException.class, () -> event.archive(beforeEnd));
    }

    @Test
    void archive_fromPublished_exactlyAtEndTime_throws() {
        Event event = EventTestFixtures.publishableDraftEvent();
        event.publish();

        Instant exactlyAtEnd = event.getEndTimeUtc();

        assertThrows(InvalidEventTransitionException.class, () -> event.archive(exactlyAtEnd));
    }

    @Test
    void archive_alreadyArchived_isIdempotentNoOp() {
        Event event = EventTestFixtures.draftEvent();
        event.archive(Instant.now());

        assertDoesNotThrow(() -> event.archive(Instant.now()));
        assertEquals(EventStatus.ARCHIVED, event.getStatus());
    }

    // --- constructor validation ---

    @Test
    void constructor_startAfterEnd_throws() {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant end = start.minus(1, ChronoUnit.HOURS);

        assertThrows(IllegalArgumentException.class, () ->
                new Event(null, "Test Event", start, end, Instant.now(), Instant.now(), "EUR"));
    }

    @Test
    void constructor_wrongCurrency_throws() {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        assertThrows(IllegalArgumentException.class, () ->
                new Event(null, "Test Event", start, end, Instant.now(), Instant.now(), "USD"));
    }
}