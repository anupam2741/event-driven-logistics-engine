package com.project.dto;

public record TrackingResponseDto(
        String orderId,
        String riderId,
        Coordinates coordinates,
        String status,
        boolean isActive
) {}