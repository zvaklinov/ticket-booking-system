// com/hari/paymentservice/webhook/StripeWebhookController.java
package com.hari.paymentservice.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeWebhookService webhookService;

    public StripeWebhookController(StripeWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * The body is taken as a raw String, never a parsed object. Stripe's signature is computed
     * over the exact bytes it sent — deserializing and re-serializing would reorder keys or
     * change whitespace and the signature would no longer match.
     */
    @PostMapping
    public ResponseEntity<Void> handle(@RequestBody String payload,
                                       @RequestHeader("Stripe-Signature") String signature) {
        try {
            webhookService.process(payload, signature);
            return ResponseEntity.ok().build();

        } catch (InvalidWebhookSignatureException e) {
            log.warn("Rejected webhook with an invalid signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();

        } catch (RuntimeException e) {
            log.error("Failed to process Stripe webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}