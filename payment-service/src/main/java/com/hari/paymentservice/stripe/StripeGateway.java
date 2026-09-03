package com.hari.paymentservice.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class StripeGateway {

    private final StripeClient stripeClient;

    public StripeGateway(@Value("${stripe.secret-key}") String secretKey) {
        this.stripeClient = new StripeClient(secretKey);
    }

    /**
     * Stripe amounts are in the smallest currency unit — cents for EUR. €50.00 is 5000, not 50.
     * Getting this wrong charges a hundred times too much or too little, and it is silent.
     */
    public PaymentIntent createPaymentIntent(UUID paymentId, BigDecimal amount, String currency)
            throws StripeException {

        long amountInCents = amount.movePointRight(2).longValueExact();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(currency.toLowerCase())
                .putMetadata("paymentId", paymentId.toString())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build())
                .build();

        // A stable key derived from our payment id. Retrying this call after a timeout returns
        // the PaymentIntent already created rather than creating a second one — which would
        // mean two authorisations for the same seats.
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("payment-intent:" + paymentId)
                .build();

        return stripeClient.paymentIntents().create(params, options);
    }

    public Refund createRefund(UUID paymentId, String paymentIntentId) throws StripeException {
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("refund:" + paymentId)
                .build();

        return stripeClient.refunds().create(params, options);
    }

    public PaymentIntent cancelPaymentIntent(String paymentIntentId) throws StripeException {
        return stripeClient.paymentIntents().cancel(paymentIntentId);
    }

    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        return stripeClient.paymentIntents().retrieve(paymentIntentId);
    }
}