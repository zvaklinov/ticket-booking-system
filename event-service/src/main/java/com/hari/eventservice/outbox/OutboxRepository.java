package com.hari.eventservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    /**
     * Claims a batch of unpublished rows for this poller.
     *
     * FOR UPDATE locks the selected rows; SKIP LOCKED makes concurrent pollers step over rows
     * another poller already holds instead of blocking on them. Two instances therefore
     * partition the work between themselves with no coordination, and neither publishes the
     * same row twice. Without SKIP LOCKED the second poller would block until the first
     * committed, serialising all publishing through one instance.
     */
    @Query(value = """
            SELECT * FROM outbox
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEntry> lockPendingBatch(@Param("batchSize") int batchSize);

    long countByPublishedAtIsNull();

    @Query(value = "SELECT min(created_at) FROM outbox WHERE published_at IS NULL",
            nativeQuery = true)
    Instant findOldestUnpublishedCreatedAt();
}