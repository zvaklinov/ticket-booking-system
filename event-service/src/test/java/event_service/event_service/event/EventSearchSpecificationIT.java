package event_service.event_service.event;

import event_service.event_service.TestcontainersConfiguration;
import event_service.event_service.category.Category;
import event_service.event_service.category.CategoryRepository;
import event_service.event_service.pricetier.EventPriceTier;
import event_service.event_service.pricetier.EventPriceTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class EventSearchSpecificationIT {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EventPriceTierRepository eventPriceTierRepository;

    private Event gapPricedEvent; // only 40 EUR and 120 EUR tiers — nothing in between

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.saveAndFlush(new Category("Music"));

        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Event event = new Event(category, "Gap Priced Concert", start, end,
                Instant.now(), start.minus(1, ChronoUnit.DAYS), "EUR");
        ReflectionTestUtils.setField(event, "venueName", "Test Arena");
        ReflectionTestUtils.setField(event, "location", "Berlin");
        ReflectionTestUtils.setField(event, "venueTimezone", "Europe/Berlin");
        event.publish();

        gapPricedEvent = eventRepository.saveAndFlush(event);

        eventPriceTierRepository.saveAndFlush(
                new EventPriceTier(gapPricedEvent.getId(), new BigDecimal("40.00"), "EUR"));
        eventPriceTierRepository.saveAndFlush(
                new EventPriceTier(gapPricedEvent.getId(), new BigDecimal("120.00"), "EUR"));
    }

    @Test
    void priceRangeSearch_doesNotMatchEventWithNoTierInRange() {
        Specification<Event> spec = EventSearchSpecification.build(
                null, null, null, null, new BigDecimal("60"), new BigDecimal("80"));

        Page<Event> results = eventRepository.findAll(spec, PageRequest.of(0, 10));

        assertTrue(results.isEmpty());
    }

    @Test
    void priceRangeSearch_matchesEventWithActualTierInRange() {
        eventPriceTierRepository.saveAndFlush(
                new EventPriceTier(gapPricedEvent.getId(), new BigDecimal("70.00"), "EUR"));

        Specification<Event> spec = EventSearchSpecification.build(
                null, null, null, null, new BigDecimal("60"), new BigDecimal("80"));

        Page<Event> results = eventRepository.findAll(spec, PageRequest.of(0, 10));

        assertEquals(1, results.getTotalElements());
    }
}