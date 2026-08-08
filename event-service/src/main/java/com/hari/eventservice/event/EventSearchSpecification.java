package com.hari.eventservice.event;

import com.hari.eventservice.pricetier.EventPriceTier;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EventSearchSpecification {

    private EventSearchSpecification() {
        // utility class — not meant to be instantiated
    }

    public static Specification<Event> build(UUID categoryId, String location,
                                             Instant dateFrom, Instant dateTo,
                                             BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Customer search only ever sees published events — not optional, always applied
            predicates.add(cb.equal(root.get("status"), EventStatus.PUBLISHED));

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            if (location != null) {
                predicates.add(cb.equal(root.get("location"), location));
            }

            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTimeUtc"), dateFrom));
            }

            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startTimeUtc"), dateTo));
            }

            if (minPrice != null || maxPrice != null) {
                Subquery<UUID> priceTierSubquery = query.subquery(UUID.class);
                Root<EventPriceTier> tier = priceTierSubquery.from(EventPriceTier.class);
                priceTierSubquery.select(tier.get("eventId"));

                List<Predicate> tierPredicates = new ArrayList<>();
                tierPredicates.add(cb.equal(tier.get("eventId"), root.get("id")));
                if (minPrice != null) {
                    tierPredicates.add(cb.greaterThanOrEqualTo(tier.get("price"), minPrice));
                }
                if (maxPrice != null) {
                    tierPredicates.add(cb.lessThanOrEqualTo(tier.get("price"), maxPrice));
                }
                priceTierSubquery.where(tierPredicates.toArray(new Predicate[0]));

                predicates.add(cb.exists(priceTierSubquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}