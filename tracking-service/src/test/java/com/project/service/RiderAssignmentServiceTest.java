package com.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.dto.Coordinates;
import com.project.dto.OrderEvent;
import com.project.dto.OrderStatusUpdateEvent;
import com.project.entity.RiderEntity;
import com.project.entity.RiderStatus;
import com.project.kafka.OrderStatusProducer;
import com.project.repository.RiderRepository;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiderAssignmentServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private RiderRepository riderRepository;
    @Mock private OrderStatusProducer orderStatusProducer;
    @Mock private ObjectMapper objectMapper;
    @Mock private RiderNavigationService riderNavigationService;
    @Mock private GeoOperations<String, Object> geoOperations;

    private RiderAssignmentService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
        service = new RiderAssignmentService(
                redisTemplate, riderRepository, orderStatusProducer, objectMapper, riderNavigationService);
    }

    private OrderEvent buildEvent() {
        return new OrderEvent(
                "order-123", "CUST001",
                new Coordinates(12.97, 77.59),
                new Coordinates(12.98, 77.61),
                150.0, "MEDIUM", "RIDER_001", "ACCEPTED", LocalDateTime.now()
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void assignRiders_claimSucceeds_movesRiderAndPublishesRiderAssigned() throws Exception {
        when(riderRepository.claimRiderIfAvailable("RIDER_001", "order-123")).thenReturn(1);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        Point point = new Point(77.59, 12.97);
        when(geoOperations.position("active_shipments", "RIDER_001")).thenReturn(List.of(point));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.assignRiders(buildEvent());

        ArgumentCaptor<OrderStatusUpdateEvent> captor = ArgumentCaptor.forClass(OrderStatusUpdateEvent.class);
        verify(orderStatusProducer).sendOrderStatusUpdateEvent(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("RIDER_ASSIGNED");
    }

    @Test
    void assignRiders_claimFails_publishesCancelledEvent() {
        when(riderRepository.claimRiderIfAvailable("RIDER_001", "order-123")).thenReturn(0);

        service.assignRiders(buildEvent());

        ArgumentCaptor<OrderStatusUpdateEvent> captor = ArgumentCaptor.forClass(OrderStatusUpdateEvent.class);
        verify(orderStatusProducer).sendOrderStatusUpdateEvent(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("CANCELLED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void assignRiders_redisMoveFails_compensatesAndThrows() {
        when(riderRepository.claimRiderIfAvailable("RIDER_001", "order-123")).thenReturn(1);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RuntimeException("Redis error"));

        assertThatThrownBy(() -> service.assignRiders(buildEvent()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Redis transition failed");

        verify(geoOperations).remove("active_shipments", "RIDER_001");
    }

    @Test
    @SuppressWarnings("unchecked")
    void assignRiders_noPositionAfterMove_skipsSimulatorBroadcast() throws Exception {
        when(riderRepository.claimRiderIfAvailable("RIDER_001", "order-123")).thenReturn(1);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);
        when(geoOperations.position("active_shipments", "RIDER_001")).thenReturn(List.of());

        service.assignRiders(buildEvent());

        verify(riderNavigationService, never()).publishRedis(any(), any());
        ArgumentCaptor<OrderStatusUpdateEvent> captor = ArgumentCaptor.forClass(OrderStatusUpdateEvent.class);
        verify(orderStatusProducer).sendOrderStatusUpdateEvent(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("RIDER_ASSIGNED");
    }

    @Test
    void releaseRider_removesFromActiveAndAddsToAvailable() {
        Coordinates lastLocation = new Coordinates(12.97, 77.59);

        service.releaseRider("RIDER_001", lastLocation);

        verify(geoOperations).remove("active_shipments", "RIDER_001");
        verify(geoOperations).add(eq("available_riders"), any(Point.class), eq("RIDER_001"));
    }

    @Test
    void markRiderAsAvailable_riderExists_updatesStatusAndClearsOrder() {
        RiderEntity rider = new RiderEntity();
        rider.setId("RIDER_001");
        rider.setStatus(RiderStatus.BUSY);
        rider.setCurrentOrderId("order-123");
        when(riderRepository.findById("RIDER_001")).thenReturn(Optional.of(rider));

        service.markRiderAsAvailable("RIDER_001");

        assertThat(rider.getStatus()).isEqualTo(RiderStatus.AVAILABLE);
        assertThat(rider.getCurrentOrderId()).isNull();
        verify(riderRepository).save(rider);
    }

    @Test
    void markRiderAsAvailable_riderNotFound_throwsException() {
        when(riderRepository.findById("RIDER_999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRiderAsAvailable("RIDER_999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
