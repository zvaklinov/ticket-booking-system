package com.hari.eventservice.category.dto;

import com.hari.eventservice.category.Category;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        Instant createdAt,
        Instant updatedAt
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(), category.getName(), category.getCreatedAt(), category.getUpdatedAt());
    }
}