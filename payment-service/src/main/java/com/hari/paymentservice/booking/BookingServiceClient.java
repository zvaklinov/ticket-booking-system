package com.hari.paymentservice.booking;

import com.hari.paymentservice.booking.exceptions.BookingServiceUnavailableException;
import com.hari.paymentservice.booking.exceptions.HoldNotPayableException;
import com.hari.paymentservice.booking.exceptions.SeatHoldNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class BookingServiceClient {

    private final RestClient bookingServiceRestClient;

    public BookingServiceClient(RestClient bookingServiceRestClient) {
        this.bookingServiceRestClient = bookingServiceRestClient;
    }

    public SeatHoldPaymentDetails getPaymentDetails(UUID holdId) {
        try {
            return bookingServiceRestClient.get()
                    .uri("/internal/seat-holds/{holdId}/payment-details", holdId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentTokenValue())
                    .retrieve()
                    .body(SeatHoldPaymentDetails.class);

        } catch (HttpClientErrorException.NotFound e) {
            throw new SeatHoldNotFoundException(holdId);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw new IllegalStateException(
                    "Booking Service rejected Payment Service's credentials for hold " + holdId, e);
        } catch (RestClientException e) {
            throw new BookingServiceUnavailableException(holdId, e);
        }
    }

    /**
     * Moves the hold ACTIVE -> PAYMENT_PENDING. Idempotent on the booking side, so a retry
     * after a timeout is safe.
     */
    public void reserveForPayment(UUID holdId) {
        try {
            bookingServiceRestClient.post()
                    .uri("/internal/seat-holds/{holdId}/reserve-for-payment", holdId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentTokenValue())
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException.Conflict e) {
            // The hold is no longer ACTIVE — expired, released, or already confirmed.
            throw new HoldNotPayableException(holdId, "the hold is no longer available for payment");
        } catch (HttpClientErrorException.NotFound e) {
            throw new SeatHoldNotFoundException(holdId);
        } catch (RestClientException e) {
            throw new BookingServiceUnavailableException(holdId, e);
        }
    }

    private String currentTokenValue() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getTokenValue();
        }
        throw new IllegalStateException("No JWT available to relay to Booking Service");
    }
}