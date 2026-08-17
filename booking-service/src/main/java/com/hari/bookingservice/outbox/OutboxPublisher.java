package com.hari.bookingservice.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxBatchPublisher batchPublisher;

    public OutboxPublisher(OutboxBatchPublisher batchPublisher) {
        this.batchPublisher = batchPublisher;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms}")
    public void poll() {
        try {
            int published = batchPublisher.publishBatch();
            if (published > 0) {
                log.debug("Published {} outbox entries", published);
            }
        } catch (RuntimeException e) {
            log.error("Outbox polling cycle failed", e);
        }
    }
}