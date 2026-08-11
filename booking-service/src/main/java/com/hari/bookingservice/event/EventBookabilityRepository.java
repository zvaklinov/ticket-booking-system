package com.hari.bookingservice.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventBookabilityRepository extends JpaRepository<EventBookability, UUID> {
}
