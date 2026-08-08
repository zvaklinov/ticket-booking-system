package com.hari.eventservice.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {
}
