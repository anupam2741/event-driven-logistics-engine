package com.project.controller;

import com.project.dto.OrderStatusUpdateEvent;
import com.project.dto.RiderLocationPing;
import com.project.kafka.OrderStatusProducer;
import com.project.service.RiderAssignmentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/tracking")
@Slf4j
public class LocationIngestionController {

    private final RedisTemplate<String, Object> redisTemplate;
    private final OrderStatusProducer orderStatusProducer;
    private final RiderAssignmentService riderAssignmentService;

    public LocationIngestionController(
            @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate,OrderStatusProducer orderStatusProducer,RiderAssignmentService riderAssignmentService) {
        this.redisTemplate = redisTemplate;
        this.orderStatusProducer = orderStatusProducer;
        this.riderAssignmentService = riderAssignmentService;
    }
    private static final String ACTIVE_SHIPMENTS_KEY = "active_shipments";

    @PostMapping("/ping")
    public ResponseEntity<Void> updateLocation(@Valid @RequestBody RiderLocationPing ping) {
        redisTemplate.opsForGeo().add(
                ACTIVE_SHIPMENTS_KEY,
                new Point(ping.coordinates().lng(), ping.coordinates().lat()),
                ping.riderId()
        );
        if (ping.status() != null) {
            OrderStatusUpdateEvent update = new OrderStatusUpdateEvent(
                    ping.orderId(),
                    ping.status(),
                    LocalDateTime.now()
            );
            orderStatusProducer.sendOrderStatusUpdateEvent(update);
            if ("DELIVERED".equals(ping.status())) {
                riderAssignmentService.releaseRider(ping.riderId(), ping.coordinates());
                riderAssignmentService.markRiderAsAvailable(ping.riderId());
            }
        }

        return ResponseEntity.accepted().build();
    }
}
