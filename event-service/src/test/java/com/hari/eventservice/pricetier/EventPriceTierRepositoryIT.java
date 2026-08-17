package com.hari.eventservice.pricetier;

import com.hari.eventservice.TestcontainersConfiguration;
import com.hari.eventservice.category.Category;
import com.hari.eventservice.category.CategoryRepository;
import com.hari.eventservice.event.Event;
import com.hari.eventservice.event.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class EventPriceTierRepositoryIT {

    @Autowired
    private EventPriceTierRepository eventPriceTierRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void duplicateEventPriceCurrency_violatesUniqueConstraint() {
        UUID eventId = createEvent().getId();

        eventPriceTierRepository.saveAndFlush(
                new EventPriceTier(eventId, new BigDecimal("70.00"), "EUR"));

        EventPriceTier duplicate = new EventPriceTier(eventId, new BigDecimal("70.00"), "EUR");

        assertThrows(DataIntegrityViolationException.class, () ->
                eventPriceTierRepository.saveAndFlush(duplicate));
    }

    private Event createEvent() {
        Category category = categoryRepository.saveAndFlush(new Category("Music"));

        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Event event = new Event(category, "Test Event", "Lorem Ipsum", "Sofia, Bulgaria", "Arena 8888",
                "GMT+3", start, end, Instant.now(), start.minus(1, ChronoUnit.DAYS),
                "EUR");

        return eventRepository.saveAndFlush(event);
    }
}