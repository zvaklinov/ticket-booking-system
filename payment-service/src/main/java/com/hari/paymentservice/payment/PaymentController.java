package com.hari.paymentservice.payment;

import com.hari.paymentservice.common.AuthenticatedUser;
import com.hari.paymentservice.payment.dto.CreatePaymentRequest;
import com.hari.paymentservice.payment.dto.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request,
                                  @RequestHeader("Idempotency-Key") String idempotencyKey,
                                  @AuthenticationPrincipal Jwt jwt) {
        return paymentService.create(request, AuthenticatedUser.idOf(jwt), idempotencyKey);
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse getById(@PathVariable UUID paymentId,
                                   @AuthenticationPrincipal Jwt jwt) {
        return paymentService.getById(paymentId, AuthenticatedUser.idOf(jwt));
    }
}