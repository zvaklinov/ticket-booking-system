package com.hari.paymentservice.payment;

import com.hari.paymentservice.booking.BookingServiceClient;
import com.hari.paymentservice.booking.exceptions.HoldNotPayableException;
import com.hari.paymentservice.booking.SeatHoldPaymentDetails;
import com.hari.paymentservice.common.IdempotencyKey;
import com.hari.paymentservice.common.IdempotencyKeyConflictException;
import com.hari.paymentservice.common.IdempotencyKeyRepository;
import com.hari.paymentservice.payment.dto.CreatePaymentRequest;
import com.hari.paymentservice.payment.dto.PaymentResponse;
import com.hari.paymentservice.payment.exceptions.HoldAlreadyHasPaymentException;
import com.hari.paymentservice.payment.exceptions.PaymentAccessDeniedException;
import com.hari.paymentservice.payment.exceptions.PaymentProviderException;
import com.hari.paymentservice.stripe.StripeGateway;
import com.hari.paymentservice.stripe.StripeStatusMapper;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentUnitOfWork transactions;
    private final PaymentRepository paymentRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final BookingServiceClient bookingServiceClient;
    private final StripeGateway stripeGateway;
    private final Clock clock;

    public PaymentService(PaymentUnitOfWork transactions,
                          PaymentRepository paymentRepository,
                          IdempotencyKeyRepository idempotencyKeyRepository,
                          BookingServiceClient bookingServiceClient,
                          StripeGateway stripeGateway,
                          Clock clock) {
        this.transactions = transactions;
        this.paymentRepository = paymentRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.bookingServiceClient = bookingServiceClient;
        this.stripeGateway = stripeGateway;
        this.clock = clock;
    }

    /**
     * Deliberately NOT @Transactional. This method makes three external calls; wrapping them in
     * a database transaction would hold row locks for their combined latency, and a rollback
     * could not undo them anyway — Stripe has no notion of our transaction.
     */
    public PaymentResponse create(CreatePaymentRequest request, UUID userId, String idempotencyKey) {
        String requestHash = hashRequest(request, userId);

        Optional<IdempotencyKey> existing =
                idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);

        if (existing.isPresent()) {
            return resumeFrom(existing.get(), requestHash, idempotencyKey);
        }

        SeatHoldPaymentDetails hold = bookingServiceClient.getPaymentDetails(request.holdId());
        validate(hold, userId);

        try {
            Payment payment = transactions.createPaymentRecord(
                    hold.holdId(), userId, hold.totalAmount(), hold.currency(),
                    idempotencyKey, requestHash);

            return resume(payment);

        } catch (DataIntegrityViolationException e) {
            Optional<IdempotencyKey> key =
                    idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);

            if (key.isPresent()) {
                return resumeFrom(key.get(), requestHash, idempotencyKey);
            }

            Optional<Payment> live =
                    paymentRepository.findByHoldIdAndStatusNotIn(hold.holdId(), PaymentStatus.DEAD);

            if (live.isPresent()) {
                throw new HoldAlreadyHasPaymentException(hold.holdId(), live.get().getId());
            }

            throw e;
        }
    }

    public PaymentResponse getById(UUID paymentId, UUID userId) {
        Payment payment = paymentRepository.requireById(paymentId);

        if (!payment.getUserId().equals(userId)) {
            throw new PaymentAccessDeniedException(paymentId);
        }

        return PaymentResponse.from(payment, null);
    }

    /**
     * Completes a payment from whatever state it is in. Safe to call repeatedly: reserving the
     * hold is idempotent on the booking side, and the Stripe call carries a stable idempotency
     * key derived from our payment id, so a retry returns the existing PaymentIntent rather
     * than creating a second one.
     */
    private PaymentResponse resume(Payment payment) {
        if (payment.getStripePaymentIntentId() != null) {
            return PaymentResponse.from(payment, retrieveClientSecret(payment));
        }

        bookingServiceClient.reserveForPayment(payment.getHoldId());

        try {
            PaymentIntent intent = stripeGateway.createPaymentIntent(
                    payment.getId(), payment.getAmount(), payment.getCurrency());

            Payment updated = transactions.attachPaymentIntent(
                    payment.getId(),
                    intent.getId(),
                    StripeStatusMapper.fromStripeStatus(intent.getStatus()));

            return PaymentResponse.from(updated, intent.getClientSecret());

        } catch (StripeException e) {
            // The Payment row stays CREATED with no PaymentIntent, and the hold stays
            // PAYMENT_PENDING. Both self-heal: the hold expires at its payment deadline and
            // the payment sweep marks this FAILED. Nothing is stuck, nothing was charged.
            log.error("Stripe rejected PaymentIntent creation for payment {}", payment.getId(), e);
            throw new PaymentProviderException(payment.getId(), e);
        }
    }

    private PaymentResponse resumeFrom(IdempotencyKey key, String requestHash, String idempotencyKey) {
        if (!key.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyConflictException(idempotencyKey);
        }
        return resume(paymentRepository.requireById(key.getResourceId()));
    }

    private String retrieveClientSecret(Payment payment) {
        try {
            return stripeGateway.retrievePaymentIntent(payment.getStripePaymentIntentId())
                    .getClientSecret();
        } catch (StripeException e) {
            throw new PaymentProviderException(payment.getId(), e);
        }
    }

    private void validate(SeatHoldPaymentDetails hold, UUID userId) {
        if (!hold.userId().equals(userId)) {
            // Identical message to the not-payable case on purpose: telling a caller "that hold
            // exists but belongs to someone else" is more information than they need.
            throw new HoldNotPayableException(hold.holdId(), "the hold is not available to you");
        }
        if (!"ACTIVE".equals(hold.status())) {
            throw new HoldNotPayableException(hold.holdId(),
                    "the hold is " + hold.status() + ", expected ACTIVE");
        }
        if (!hold.expiresAt().isAfter(clock.instant())) {
            throw new HoldNotPayableException(hold.holdId(), "the hold has expired");
        }
    }

    private String hashRequest(CreatePaymentRequest request, UUID userId) {
        String canonical = request.holdId() + "|" + userId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}