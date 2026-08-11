package com.hari.bookingservice.event;

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
                    .retrieve()
                    .body(EventSummary.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new EventNotFoundException(eventId);
        } catch (RestClientException e) {
            throw new EventServiceUnavailableException(eventId, e);
        }
    }
}
