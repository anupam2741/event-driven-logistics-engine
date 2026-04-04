package com.project.dto;

import java.time.LocalDateTime;
public record OrderStatusUpdateEvent(String orderId,
                                     String status,
                                     LocalDateTime updatedAt
) {}
