package event_service.event_service.event;

import event_service.event_service.category.Category;
import event_service.event_service.category.CategoryRepository;
import event_service.event_service.common.EntityNotFoundException;
import event_service.event_service.event.dto.EventResponse;
import org.hibernate.service.spi.InjectService;
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
    private final Clock clock;

    public EventService(EventRepository eventRepository, CategoryRepository categoryRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    @Transactional
    public EventResponse create(UUID categoryId, String title, Instant startTimeUtc, Instant endTimeUtc,
                                Instant bookingOpensAtUtc, Instant bookingClosesAtUtc, String currency) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + categoryId));

        Event event = new Event(category, title, startTimeUtc, endTimeUtc,
                bookingOpensAtUtc, bookingClosesAtUtc, currency);

        return EventResponse.from(eventRepository.saveAndFlush(event));
    }

    @Transactional(readOnly = true)
    public EventResponse getById(UUID eventId) {
        return EventResponse.from(findOrThrow(eventId));
    }

    @Transactional
    public void publish(UUID eventId) {
        findOrThrow(eventId).publish();
    }

    @Transactional
    public void cancel(UUID eventId) {
        findOrThrow(eventId).cancel();
    }

    @Transactional
    public void archive(UUID eventId) {
        findOrThrow(eventId).archive(clock.instant());
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
}
