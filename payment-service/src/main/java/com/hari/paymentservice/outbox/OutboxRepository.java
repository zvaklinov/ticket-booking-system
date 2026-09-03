package com.hari.paymentservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

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