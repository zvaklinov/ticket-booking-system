package com.hari.paymentservice.payment;

import com.hari.paymentservice.payment.exceptions.PaymentNotFoundException;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByHoldIdAndStatusNotIn(UUID holdId, List<PaymentStatus> excluded);

    List<Payment> findByStatusInAndCreatedAtBefore(
            List<PaymentStatus> statuses, Instant cutoff, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") UUID paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.holdId = :holdId AND p.status NOT IN :dead")
    Optional<Payment> findByHoldIdForUpdate(@Param("holdId") UUID holdId,
                                            @Param("dead") List<PaymentStatus> dead);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.stripePaymentIntentId = :paymentIntentId")
    Optional<Payment> findByStripePaymentIntentIdForUpdate(
            @Param("paymentIntentId") String paymentIntentId);

    @Transactional
    @Query(value = """
        UPDATE payment
        SET refund_submitted_at = :now
        WHERE id IN (
            SELECT id FROM payment
            WHERE status = 'REFUND_PENDING'
              AND (refund_submitted_at IS NULL OR refund_submitted_at < :staleBefore)
            ORDER BY updated_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        )
        RETURNING id
        """, nativeQuery = true)
    List<UUID> claimRefundsToSubmit(@Param("now") Instant now,
                                    @Param("staleBefore") Instant staleBefore,
                                    @Param("batchSize") int batchSize);

    List<Payment> findByStatusAndRefundAttemptCountLessThanAndUpdatedAtBefore(
            PaymentStatus status, int maxAttempts, Instant before, Pageable pageable);

    default Payment requireById(UUID paymentId) {
        return findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    long countByStatusAndRefundAttemptCountGreaterThanEqual(PaymentStatus status, int attempts);
}