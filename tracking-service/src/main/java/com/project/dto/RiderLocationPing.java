package com.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RiderLocationPing(
        @NotBlank(message = "riderId is required")
        String riderId,

        String orderId,

        String status,

        @NotNull(message = "coordinates are required")
        @Valid
        Coordinates coordinates
) {}
