package com.hari.eventservice.outbox;

import com.hari.eventservice.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the one property that justifies the outbox pattern: the event row and the domain
 * change share a transaction, so they commit together or not at all.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxIT {

    @Autowired private OutboxRepository outboxRepository;
    @Autowired private OutboxWriter outboxWriter;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE outbox, processed_message CASCADE");
    }

    @Test
    void writingToTheOutboxCreatesAPendingEntry() {
        UUID aggregateId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status ->
                outboxWriter.write("EventPublished", aggregateId, samplePayload(aggregateId)));

        List<OutboxEntry> entries = outboxRepository.findAll();
        assertEquals(1, entries.size());

        OutboxEntry entry = entries.get(0);
        assertEquals("EventPublished", entry.getEventType());
        assertEquals(aggregateId, entry.getAggregateId());
        assertEquals(0, entry.getAttemptCount());
        assertEquals(1, outboxRepository.countByPublishedAtIsNull());
    }

    @Test
    void theOutboxRowIsRolledBackWithTheEnclosingTransaction() {
        UUID aggregateId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            outboxWriter.write("EventPublished", aggregateId, samplePayload(aggregateId));
            // Simulates the domain change failing after the outbox row was written. If the row
            // survived this, the system would announce a fact that never actually happened.
            status.setRollbackOnly();
        });

        assertEquals(0, outboxRepository.count(),
                "an outbox entry must not survive a transaction that rolled back");
    }

    @Test
    void theStoredPayloadIsACompleteEnvelope() {
        UUID aggregateId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status ->
                outboxWriter.write("EventPublished", aggregateId, samplePayload(aggregateId)));

        JsonNode envelope = jsonMapper.readTree(outboxRepository.findAll().get(0).getPayload());

        assertFalse(envelope.get("eventId").isNull());
        assertEquals("EventPublished", envelope.get("eventType").asString());
        assertFalse(envelope.get("eventVersion").isNull());
        assertFalse(envelope.get("occurredAt").isNull());
        assertEquals("event-service", envelope.get("producer").asString());
        assertFalse(envelope.get("correlationId").isNull());
        assertEquals(aggregateId.toString(), envelope.get("aggregateId").asString());
        assertFalse(envelope.get("payloadSchemaVersion").isNull());
        assertFalse(envelope.get("payload").isNull());

        assertEquals(aggregateId.toString(), envelope.get("payload").get("eventId").asString());
    }

    @Test
    void occurredAtSerialisesAsAnIso8601StringNotAnEpochNumber() {
        UUID aggregateId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status ->
                outboxWriter.write("EventPublished", aggregateId, samplePayload(aggregateId)));

        JsonNode envelope = jsonMapper.readTree(outboxRepository.findAll().get(0).getPayload());

        assertTrue(envelope.get("occurredAt").isString(),
                () -> "occurredAt should be an ISO-8601 string but was: " + envelope.get("occurredAt"));
    }

    @Test
    void eachWriteGetsItsOwnMessageIdSoConsumersCanDeduplicate() {
        UUID aggregateId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            outboxWriter.write("EventPublished", aggregateId, samplePayload(aggregateId));
            outboxWriter.write("EventCancelled", aggregateId, samplePayload(aggregateId));
        });

        List<String> messageIds = outboxRepository.findAll().stream()
                .map(entry -> jsonMapper.readTree(entry.getPayload()).get("eventId").asString())
                .distinct()
                .toList();

        assertEquals(2, messageIds.size());
    }

    @Test
    void multipleWritesInOneTransactionAllCommitTogether() {
        UUID aggregateId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            outboxWriter.write("EventPublished", aggregateId, samplePayload(aggregateId));
            outboxWriter.write("EventUpdated", aggregateId, samplePayload(aggregateId));
            outboxWriter.write("EventCancelled", aggregateId, samplePayload(aggregateId));
        });

        assertEquals(3, outboxRepository.count());
    }

    private Object samplePayload(UUID eventId) {
        // A minimal stand-in for a real payload record — these tests are about the envelope
        // and transaction semantics, not any particular event's fields.
        return new SamplePayload(eventId, "PUBLISHED");
    }

    private record SamplePayload(UUID eventId, String status) {
    }
}