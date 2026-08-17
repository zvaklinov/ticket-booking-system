package com.hari.bookingservice.event;

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
public class EventServiceClient {

    private final RestClient eventServiceRestClient;

    public EventServiceClient(RestClient eventServiceRestClient) {
        this.eventServiceRestClient = eventServiceRestClient;
    }

    public EventSummary getEvent(UUID eventId) {
        try {
            return eventServiceRestClient.get()
                    .uri("/events/{id}", eventId)
                    // TODO Phase 8: replace token relay with the service's own credentials
                    // (client credentials or mTLS). Relaying works only for request-initiated
                    // calls and ties this call's permissions to whoever happened to trigger it.
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentTokenValue())
                    .retrieve()
                    .body(EventSummary.class);

        } catch (HttpClientErrorException.NotFound e) {
            throw new EventNotFoundException(eventId);

        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw new IllegalStateException(
                    "Event Service rejected Booking Service's credentials for event " + eventId, e);

        } catch (RestClientException e) {
            throw new EventServiceUnavailableException(eventId, e);
        }
    }

    private String currentTokenValue() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getTokenValue();
        }
        throw new IllegalStateException("No JWT available to relay to Event Service");
    }
}