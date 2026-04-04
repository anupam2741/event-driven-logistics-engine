package com.project.interfaces;

import com.project.dto.OrderRequestDto;
import com.project.dto.OrderResponseDto;
import com.project.dto.TrackingResponseDto;

public interface OrderService {
    OrderResponseDto createOrder(OrderRequestDto orderRequestDto);
    OrderResponseDto getOrderDetails(String orderId);
    TrackingResponseDto getLiveTracking(String orderId);
    OrderResponseDto cancelOrder(String orderId);
}
