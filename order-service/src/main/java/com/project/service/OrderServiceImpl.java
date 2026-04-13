package com.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.entity.OrderEntity;
import com.project.entity.OrderPriority;
import com.project.entity.OrderStatus;
import com.project.entity.OutboxEvent;
import com.project.dto.*;
import com.project.exception.OrderCancellationException;
import com.project.exception.OrderNotFoundException;
import com.project.grpc.AvailabilityResponse;
import com.project.interfaces.OrderService;
import com.project.kafka.KafkaTopics;
import com.project.interfaces.OrderTrackingInfo;
import com.project.repository.OrderRepository;
import com.project.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final OrderSafetyNetService orderSafetyNetService;
    private final OrderTrackingClient trackingClient;

    @Override
    @Transactional
    public OrderResponseDto createOrder(OrderRequestDto orderMessage){

        AvailabilityResponse availabilityResponse = orderSafetyNetService.canPlaceOrder(orderMessage.deliveryAddress().lat(),orderMessage.deliveryAddress().lng());
        boolean isRiderAvailable = availabilityResponse.getIsAvailable();
        if(isRiderAvailable){
        OrderEntity orderEntity = OrderEntity.builder()
                .customerId(orderMessage.customerId())
                .pickupAddress(orderMessage.pickupAddress())
                .deliveryAddress(orderMessage.deliveryAddress())
                .totalAmount(orderMessage.totalAmount().doubleValue())
                .status(OrderStatus.ACCEPTED)
                .priority(OrderPriority.valueOf(orderMessage.priority().toUpperCase()))
                .riderId(availabilityResponse.getRiderId())
                .build();
        OrderEntity saveOrder = orderRepository.save(orderEntity);

        OrderEvent orderEvent = new OrderEvent(saveOrder.getId().toString(),
                saveOrder.getCustomerId(),
                orderMessage.pickupAddress(),
                orderMessage.deliveryAddress(),
                saveOrder.getTotalAmount(),
                saveOrder.getPriority().name(),
                saveOrder.getRiderId(),
                saveOrder.getStatus().name(),
                saveOrder.getCreatedAt());
        try {
            String payload = objectMapper.writeValueAsString(orderEvent);
            outboxRepository.save(new OutboxEvent(saveOrder.getId().toString(), KafkaTopics.ORDER_CREATED, payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize order event for outbox", e);
        }

        return new OrderResponseDto(orderEntity.getId().toString(),orderEntity.getStatus().toString(),"Order Placed");

    }
        return new OrderResponseDto("Order Not Placed",OrderStatus.CANCELLED.name(),"No Riders Available");
        }
    @Override
    public OrderResponseDto getOrderDetails(String orderId){
        OrderEntity order = orderRepository.findById(UUID.fromString(orderId))
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        return new OrderResponseDto(
                order.getId().toString(),
                order.getStatus().name(),
                "Rider: " + (order.getRiderId() != null ? order.getRiderId() : "Not assigned")
        );
    }
    @Override
    public TrackingResponseDto getLiveTracking(String orderId){
        OrderTrackingInfo info = orderRepository.findTrackingInfoById(UUID.fromString(orderId))
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (info.getRiderId() == null) {
            return new TrackingResponseDto(orderId, null, new Coordinates(0.0,0.0), info.getStatus(), false);
        }

        return trackingClient.fetchLiveLocation(orderId, info.getRiderId(), info.getStatus());
    }
    @Override
    @Transactional
    public OrderResponseDto cancelOrder(String orderId) {
        OrderEntity order = orderRepository.findById(UUID.fromString(orderId))
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        // Only ACCEPTED or RIDER_ASSIGNED orders can be cancelled
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderCancellationException(
                    "Cannot cancel order in " + order.getStatus() + " state");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        if (order.getRiderId() != null) {
            trackingClient.cancelOrder(order.getRiderId(), orderId);
            log.info("Rider {} release requested via gRPC for cancelled order {}", order.getRiderId(), orderId);
        } else {
            log.info("Order {} cancelled before rider assignment. No gRPC call needed.", orderId);
        }

        return new OrderResponseDto(orderId, OrderStatus.CANCELLED.name(), "Order cancelled successfully");
    }

}
