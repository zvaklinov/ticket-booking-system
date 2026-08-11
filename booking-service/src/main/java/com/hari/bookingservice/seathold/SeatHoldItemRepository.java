package com.hari.bookingservice.seathold;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SeatHoldItemRepository extends JpaRepository<SeatHoldItem, UUID> {

    List<SeatHoldItem> findBySeatHoldId(UUID seatHoldId);
}
