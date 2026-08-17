package com.hari.bookingservice.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Component
class OutboxBatchPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxBatchPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final Counter publishFailures;
    private final int batchSize;

    OutboxBatchPublisher(OutboxRepository outboxRepository,
                         KafkaTemplate<String, String> kafkaTemplate,
                         Clock clock,
                         MeterRegistry registry,
                         @Value("${outbox.batch-size}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.batchSize = batchSize;
        this.publishFailures = Counter.builder("outbox.publish.failures")
                .description("Failed attempts to publish an outbox row")
                .register(registry);
    }

    @Transactional
    int publishBatch() {
        List<OutboxEntry> batch = outboxRepository.lockPendingBatch(batchSize);
        int published = 0;

        for (OutboxEntry entry : batch) {
            try {
                // Synchronous send: we must know it succeeded before marking the row published.
                // The aggregate id is the message key, so all events for one aggregate land on
                // the same partition and therefore stay ordered relative to each other.
                kafkaTemplate.send(
                        KafkaTopicConfig.BOOKING_LIFECYCLE_TOPIC,
                        entry.getAggregateId().toString(),
                        entry.getPayload()
                ).get();

                entry.markPublished(clock.instant());
                published++;

            } catch (Exception e) {
                entry.recordFailure(e.getMessage());
                publishFailures.increment();
                log.warn("Failed to publish outbox entry {} ({}), attempt {}",
                        entry.getId(), entry.getEventType(), entry.getAttemptCount(), e);

                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        return published;
    }
}