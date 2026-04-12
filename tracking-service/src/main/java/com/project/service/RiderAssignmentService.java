package com.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.dto.AssignmentMessage;
import com.project.dto.Coordinates;
import com.project.dto.OrderEvent;
import com.project.dto.OrderStatusUpdateEvent;
import com.project.entity.RiderEntity;
import com.project.entity.RiderStatus;
import com.project.kafka.OrderStatusProducer;
import com.project.repository.RiderRepository;
import jakarta.transaction.Transactional;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class RiderAssignmentService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RiderRepository riderRepository;
    private final OrderStatusProducer orderStatusProducer;
    private final ObjectMapper objectMapper;
    private final RiderNavigationService riderNavigationService;

    public RiderAssignmentService(@Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate, RiderRepository riderRepository, OrderStatusProducer orderStatusProducer, ObjectMapper objectMapper, RiderNavigationService riderNavigationService) {
        this.redisTemplate = redisTemplate;
        this.riderRepository = riderRepository;
        this.orderStatusProducer = orderStatusProducer;
        this.objectMapper = objectMapper;
        this.riderNavigationService = riderNavigationService;
    }

    private static final String AVAILABLE_RIDERS_KEY = "available_riders";
    private static final String ACTIVE_SHIPMENTS_KEY = "active_shipments";

    @Transactional // CRITICAL: Ensures DB and Redis logic happens as one unit
    public void assignRiders(OrderEvent orderEvent) {
        String riderId = orderEvent.riderId();
        String orderId = orderEvent.orderId();
        String lockKey = "lock:rider:" + riderId;

        log.info("Attempting to finalize assignment for Rider: {} and Order: {}", riderId, orderId);

        // 1. Permanent DB update: Claim the rider atomically
        int updatedRows = riderRepository.claimRiderIfAvailable(riderId, orderId);

        if (updatedRows > 0) {
            // 2. MOVE FIRST: Make the rider undiscoverable for new gRPC checks
            moveRiderToActiveIndex(riderId);
            try {
                var positions = redisTemplate.opsForGeo().position("active_shipments", riderId);
                if (positions == null || positions.isEmpty() || positions.getFirst() == null) {
                    log.warn("No position found in active_shipments for rider {} after move — skipping mission broadcast", riderId);
                } else {
                    Point point = positions.getFirst();
                    AssignmentMessage message = new AssignmentMessage(
                            orderEvent.riderId(),
                            orderEvent.orderId(),
                            new Coordinates(point.getY(), point.getX()),
                            new Coordinates(orderEvent.pickupAddress().lat(), orderEvent.pickupAddress().lng()),
                            new Coordinates(orderEvent.deliveryAddress().lat(), orderEvent.deliveryAddress().lng())
                    );
                    String json = objectMapper.writeValueAsString(message);
                    riderNavigationService.publishRedis("rider_missions", json);
                    log.info("Broadcasted assignment for {} to simulator", riderId);
                }
            }
            catch (JsonProcessingException e) {
                log.error("Failed to serialize mission for rider {}", orderEvent.riderId());
            }

            // 3. DELETE LOCK LAST: The "Soft Lock" is no longer needed
            redisTemplate.delete(lockKey);

            // 4. Notify Order Service via Kafka
            OrderStatusUpdateEvent update = new OrderStatusUpdateEvent(
                    orderId,
                    "RIDER_ASSIGNED",
                    LocalDateTime.now()
            );
            orderStatusProducer.sendOrderStatusUpdateEvent(update);

            log.info("Successfully assigned Rider {} to Order {}", riderId, orderId);
            return;
        }

        // If we reach here, the 'Soft Lock' expired or someone else claimed them
        log.warn("Failed to assign Rider {}. Either status changed or lock expired.", riderId);
    }

    private void moveRiderToActiveIndex(String riderId) {
        try {
            // Get last known position from the availability index
            var pos = redisTemplate.opsForGeo().position(AVAILABLE_RIDERS_KEY, riderId);
            if (pos != null && !pos.isEmpty()) {
                // Add to active shipments for tracking
                redisTemplate.opsForGeo().add(ACTIVE_SHIPMENTS_KEY, pos.getFirst(), riderId);
                // Remove from available pool
                redisTemplate.opsForGeo().remove(AVAILABLE_RIDERS_KEY, riderId);
            }
        } catch (Exception e) {
            log.error("Error transitioning rider {} to active index", riderId, e);
        }
    }
    public void releaseRider(String riderId, Coordinates lastLocation) {
        redisTemplate.opsForGeo().remove("active_shipments", riderId);
        redisTemplate.opsForGeo().add(
                "available_riders",
                new Point(lastLocation.lng(), lastLocation.lat()),
                riderId
        );
        log.info("Rider {} is now AVAILABLE at {}, {}", riderId, lastLocation.lat(), lastLocation.lng());
    }
    @Transactional
    public void markRiderAsAvailable(String riderId) {
        RiderEntity rider = riderRepository.findById(riderId)
                .orElseThrow(() -> new ResourceNotFoundException("Rider not found"));

        // 1. Clear the current order association
        rider.setCurrentOrderId(null);

        // 2. Set status to AVAILABLE
        rider.setStatus(RiderStatus.AVAILABLE);
        riderRepository.save(rider);

        log.info("Database Sync: Rider {} is now AVAILABLE and unlinked from any order.", riderId);
    }
}
