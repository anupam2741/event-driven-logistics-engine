package com.project.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderEvent(
        String orderId,
        String customerId,
        Coordinates pickupAddress,
        Coordinates deliveryAddress,
        BigDecimal totalAmount,
        String priority,
        String riderId,
        String status,
        LocalDateTime createdAt
) implements Serializable {
}
