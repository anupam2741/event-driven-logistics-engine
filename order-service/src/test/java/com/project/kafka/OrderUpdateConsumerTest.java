package com.project.kafka;

import com.project.dto.OrderStatusUpdateEvent;
import com.project.entity.OrderEntity;
import com.project.entity.OrderPriority;
import com.project.entity.OrderStatus;
import com.project.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderUpdateConsumerTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderUpdateConsumer consumer;

    private UUID orderId;
    private OrderEntity order;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        order = OrderEntity.builder()
                .customerId("CUST001")
                .status(OrderStatus.ACCEPTED)
                .priority(OrderPriority.MEDIUM)
                .totalAmount(100.0)
                .build();
        order.setId(orderId);
    }

    @Test
    void handleStatusUpdate_higherOrdinal_updatesStatus() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        OrderStatusUpdateEvent event = new OrderStatusUpdateEvent(
                orderId.toString(), "RIDER_ASSIGNED", LocalDateTime.now());

        consumer.handleStatusUpdate(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RIDER_ASSIGNED);
        verify(orderRepository).save(order);
    }

    @Test
    void handleStatusUpdate_lowerOrdinal_skipsUpdate() {
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        OrderStatusUpdateEvent event = new OrderStatusUpdateEvent(
                orderId.toString(), "PICKED_UP", LocalDateTime.now());

        consumer.handleStatusUpdate(event);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void handleStatusUpdate_sameOrdinal_skipsUpdate() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        OrderStatusUpdateEvent event = new OrderStatusUpdateEvent(
                orderId.toString(), "ACCEPTED", LocalDateTime.now());

        consumer.handleStatusUpdate(event);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void handleStatusUpdate_orderNotFound_doesNothing() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        OrderStatusUpdateEvent event = new OrderStatusUpdateEvent(
                orderId.toString(), "RIDER_ASSIGNED", LocalDateTime.now());

        consumer.handleStatusUpdate(event);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void handleDlt_doesNotThrow() {
        OrderStatusUpdateEvent event = new OrderStatusUpdateEvent(
                UUID.randomUUID().toString(), "RIDER_ASSIGNED", LocalDateTime.now());

        consumer.handleDlt(event, "order-status-updates-dlt");
        // no exception expected
    }
}
