package com.hari.paymentservice.payment;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    Optional<Payment> findByHoldIdAndStatusNotIn(UUID holdId, List<PaymentStatus> excluded);

    List<Payment> findByStatusInAndCreatedAtBefore(
            List<PaymentStatus> statuses, Instant cutoff, Pageable pageable);

    List<Payment> findByStatus(PaymentStatus status, Pageable pageable);
}
