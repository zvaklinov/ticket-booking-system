package com.hari.eventservice.event;

import com.hari.eventservice.category.Category;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

class EventTestFixtures {

    private EventTestFixtures() {
    }

    static Event draftEvent() {
        Category category = new Category("Music");
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Instant bookingOpens = Instant.now();
        Instant bookingCloses = start.minus(1, ChronoUnit.DAYS);
        return new Event(category, "Test Event", start, end, bookingOpens, bookingCloses, "EUR");
    }

    static Event publishableDraftEvent() {
        Event event = draftEvent();
        ReflectionTestUtils.setField(event, "venueName", "Test Arena");
        ReflectionTestUtils.setField(event, "location", "Test Location");
        ReflectionTestUtils.setField(event, "venueTimezone", "GMT+3");

        return event;
    }

    static Event publishableDraftWithBadBookingWindow() {
        Category category = new Category("Music");
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Instant bookingCloses = Instant.now();
        Instant bookingOpens = start; // opens after it closes — invalid

        Event event = new Event(category, "Test Event", start, end, bookingOpens, bookingCloses, "EUR");
        ReflectionTestUtils.setField(event, "venueName", "Test Arena");
        ReflectionTestUtils.setField(event, "location", "Test Location");
        ReflectionTestUtils.setField(event, "venueTimezone", "GMT+3");
        return event;
    }
}