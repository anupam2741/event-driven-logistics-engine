package com.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OrderRequestDto(
    @NotBlank(message = "Customer ID is required")
    String customerId,
    @NotNull(message = "pickupAddress is required")
    Coordinates pickupAddress,
    @NotNull(message = "deliveryAddress is required")
    Coordinates deliveryAddress,
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    BigDecimal totalAmount,
    @NotBlank(message = "Priority is required (HIGH/MEDIUM/LOW)")
    String priority
){}
