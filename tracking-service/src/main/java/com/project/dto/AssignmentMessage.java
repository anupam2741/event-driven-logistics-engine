package com.project.dto;

public record AssignmentMessage(
        String riderId,
        String orderId,
        Coordinates current,
        Coordinates pickup,
        Coordinates delivery
) {}
