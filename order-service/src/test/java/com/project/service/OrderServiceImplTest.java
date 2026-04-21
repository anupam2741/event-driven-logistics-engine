package com.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.dto.Coordinates;
import com.project.dto.OrderRequestDto;
import com.project.dto.OrderResponseDto;
import com.project.entity.OrderEntity;
import com.project.entity.OrderPriority;
import com.project.entity.OrderStatus;
import com.project.entity.OutboxEvent;
import com.project.exception.OrderCancellationException;
import com.project.exception.OrderNotFoundException;
import com.project.grpc.AvailabilityResponse;
import com.project.interfaces.OrderTrackingInfo;
import com.project.repository.OrderRepository;
import com.project.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private OrderSafetyNetService orderSafetyNetService;
    @Mock private OrderTrackingClient trackingClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private static final Coordinates PICKUP = new Coordinates(12.97, 77.59);
    private static final Coordinates DELIVERY = new Coordinates(12.98, 77.61);
    private static final String RIDER_ID = "RIDER_001";

    private OrderRequestDto validRequest() {
        return new OrderRequestDto("CUST001", PICKUP, DELIVERY, BigDecimal.valueOf(150.0), "MEDIUM");
    }

    private OrderEntity savedEntity(UUID id) {
        OrderEntity e = OrderEntity.builder()
                .customerId("CUST001")
                .pickupAddress(PICKUP)
                .deliveryAddress(DELIVERY)
                .totalAmount(150.0)
                .status(OrderStatus.ACCEPTED)
                .priority(OrderPriority.MEDIUM)
                .riderId(RIDER_ID)
                .build();
        e.setId(id);
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }

    @Test
    void createOrder_riderAvailable_savesOrderAndOutboxEvent() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderSafetyNetService.canPlaceOrder(DELIVERY.lat(), DELIVERY.lng()))
                .thenReturn(AvailabilityResponse.newBuilder().setIsAvailable(true).setRiderId(RIDER_ID).build());
        when(orderRepository.save(any())).thenAnswer(inv -> {
            OrderEntity e = inv.getArgument(0);
            e.setId(id);
            e.setCreatedAt(LocalDateTime.now());
            return e;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"orderId\":\"" + id + "\"}");

        OrderResponseDto response = orderService.createOrder(validRequest());

        assertThat(response.status()).isEqualTo("ACCEPTED");
        verify(orderRepository).save(any(OrderEntity.class));
        verify(outboxRepository).save(any(OutboxEvent.class));
    }

    @Test
    void createOrder_noRiderAvailable_returnsCancelled() {
        when(orderSafetyNetService.canPlaceOrder(DELIVERY.lat(), DELIVERY.lng()))
                .thenReturn(AvailabilityResponse.newBuilder().setIsAvailable(false).build());

        OrderResponseDto response = orderService.createOrder(validRequest());

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED.name());
        verify(orderRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void createOrder_outboxSerializationFails_throwsRuntime() throws Exception {
        when(orderSafetyNetService.canPlaceOrder(DELIVERY.lat(), DELIVERY.lng()))
                .thenReturn(AvailabilityResponse.newBuilder().setIsAvailable(true).setRiderId(RIDER_ID).build());
        when(orderRepository.save(any())).thenAnswer(inv -> {
            OrderEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            e.setCreatedAt(LocalDateTime.now());
            return e;
        });
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("error") {});

        assertThatThrownBy(() -> orderService.createOrder(validRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to serialize order event");
    }

    @Test
    void getOrderDetails_orderExists_returnsResponse() {
        UUID id = UUID.randomUUID();
        OrderEntity entity = savedEntity(id);
        when(orderRepository.findById(id)).thenReturn(Optional.of(entity));

        OrderResponseDto response = orderService.getOrderDetails(id.toString());

        assertThat(response.orderId()).isEqualTo(id.toString());
        assertThat(response.status()).isEqualTo("ACCEPTED");
    }

    @Test
    void getOrderDetails_orderNotFound_throwsOrderNotFoundException() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderDetails(id.toString()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void cancelOrder_acceptedOrder_cancelsAndCallsGrpc() {
        UUID id = UUID.randomUUID();
        OrderEntity entity = savedEntity(id);
        entity.setStatus(OrderStatus.ACCEPTED);
        when(orderRepository.findById(id)).thenReturn(Optional.of(entity));
        when(orderRepository.save(any())).thenReturn(entity);

        OrderResponseDto response = orderService.cancelOrder(id.toString());

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED.name());
        verify(trackingClient).cancelOrder(RIDER_ID, id.toString());
    }

    @Test
    void cancelOrder_noRider_cancelsWithoutGrpc() {
        UUID id = UUID.randomUUID();
        OrderEntity entity = savedEntity(id);
        entity.setStatus(OrderStatus.ACCEPTED);
        entity.setRiderId(null);
        when(orderRepository.findById(id)).thenReturn(Optional.of(entity));
        when(orderRepository.save(any())).thenReturn(entity);

        orderService.cancelOrder(id.toString());

        verify(trackingClient, never()).cancelOrder(any(), any());
    }

    @Test
    void cancelOrder_deliveredOrder_throwsCancellationException() {
        UUID id = UUID.randomUUID();
        OrderEntity entity = savedEntity(id);
        entity.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(id)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> orderService.cancelOrder(id.toString()))
                .isInstanceOf(OrderCancellationException.class);
    }

    @Test
    void cancelOrder_orderNotFound_throwsOrderNotFoundException() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(id.toString()))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
