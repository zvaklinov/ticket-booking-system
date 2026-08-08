package com.hari.eventservice.pricetier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface EventPriceTierRepository extends JpaRepository<EventPriceTier, UUID>, JpaSpecificationExecutor<EventPriceTier> {
}
