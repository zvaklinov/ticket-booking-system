package com.hari.eventservice.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class OutboxMetrics {

    /**
     * Both gauges hit the database on every scrape. Acceptable here — the queries are indexed
     * and cheap — but worth knowing rather than discovering under load.
     */
    public OutboxMetrics(MeterRegistry registry, OutboxRepository outboxRepository, Clock clock) {

        Gauge.builder("outbox.unpublished.count", outboxRepository,
                        OutboxRepository::countByPublishedAtIsNull)
                .description("Outbox rows not yet published to Kafka")
                .register(registry);

        // The more useful of the two. A non-zero count is normal — the poller runs every two
        // seconds. A count that stays non-zero and keeps ageing means publishing is broken.
        Gauge.builder("outbox.unpublished.oldest.age.seconds", outboxRepository, repository -> {
                    Instant oldest = repository.findOldestUnpublishedCreatedAt();
                    return oldest == null ? 0d : Duration.between(oldest, clock.instant()).toSeconds();
                })
                .description("Age in seconds of the oldest unpublished outbox row")
                .register(registry);
    }
}