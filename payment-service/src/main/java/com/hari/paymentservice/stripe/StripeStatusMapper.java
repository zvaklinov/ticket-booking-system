package com.hari.paymentservice.stripe;

import com.hari.paymentservice.payment.PaymentStatus;

/**
 * Maps Stripe PaymentIntent statuses to the internal model. Deliberately explicit rather than
 * a name-matching convention, because the two vocabularies do not correspond one-to-one:
 *
 *  - Stripe has no terminal "failed" status. A declined card returns the PaymentIntent to
 *    requires_payment_method so the customer can retry with a different card. Our FAILED means
 *    "we gave up", which Stripe never says on its own.
 *  - requires_payment_method therefore means two different things depending on history:
 *    the fresh initial state, or the state after a decline. The Payment's own status and its
 *    PaymentAttempt rows are what distinguish them.
 *  - requires_capture only occurs with manual capture, which we do not use.
 */
public final class StripeStatusMapper {

    private StripeStatusMapper() {
    }

    public static PaymentStatus fromStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "requires_payment_method", "requires_confirmation" ->
                    PaymentStatus.REQUIRES_PAYMENT_METHOD;
            case "requires_action" -> PaymentStatus.REQUIRES_ACTION;
            case "processing", "requires_capture" -> PaymentStatus.PROCESSING;
            case "succeeded" -> PaymentStatus.SUCCEEDED;
            case "canceled" -> PaymentStatus.CANCELLED;
            default -> throw new IllegalArgumentException(
                    "Unmapped Stripe PaymentIntent status: " + stripeStatus);
        };
    }
}