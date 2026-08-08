package com.hari.eventservice.category.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(
        @NotBlank String name
) {
}