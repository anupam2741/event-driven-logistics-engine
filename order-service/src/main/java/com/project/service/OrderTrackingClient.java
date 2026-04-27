package com.project.service;

import com.project.dto.Coordinates;
import com.project.dto.TrackingResponseDto;
import com.project.entity.PendingRiderRelease;
import com.project.grpc.*;
import com.project.repository.PendingRiderReleaseRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderTrackingClient {

    private final PendingRiderReleaseRepository pendingRiderReleaseRepository;

    @GrpcClient("tracking-service")
    private RiderDiscoveryServiceGrpc.RiderDiscoveryServiceBlockingStub trackingStub;

    @CircuitBreaker(name = "fetchLiveLocation", fallbackMethod = "fetchLiveLocationFallback")
    public TrackingResponseDto fetchLiveLocation(String orderId, String riderId, String orderStatus) {
        LocationRequest request = LocationRequest.newBuilder()
                .setRiderId(riderId)
                .build();
        LocationResponse response = trackingStub.getLiveLocation(request);
        return new TrackingResponseDto(
                orderId, riderId,
                new Coordinates(response.getLatitude(), response.getLongitude()),
                orderStatus, response.getIsActive()
        );
    }

    private TrackingResponseDto fetchLiveLocationFallback(String orderId, String riderId,
                                                          String orderStatus, Throwable t) {
        log.warn("Circuit breaker open for fetchLiveLocation — returning stale fallback. Cause: {}", t.getMessage());
        return new TrackingResponseDto(orderId, riderId, new Coordinates(0.0, 0.0), orderStatus, false);
    }

    @CircuitBreaker(name = "cancelOrder", fallbackMethod = "cancelOrderFallback")
    public void cancelOrder(String riderId, String orderId) {
        ReleaseRiderRequest request = ReleaseRiderRequest.newBuilder()
                .setRiderId(riderId)
                .setOrderId(orderId)
                .build();
        ReleaseRiderResponse response = trackingStub.releaseRider(request);
        if (response.getSuccess()) {
            log.info("Successfully released rider {} via gRPC", riderId);
        }
    }

    private void cancelOrderFallback(String riderId, String orderId, Throwable t) {
        log.warn("Circuit breaker open for cancelOrder — queuing compensation for rider {}. Cause: {}",
                riderId, t.getMessage());
        try {
            pendingRiderReleaseRepository.save(new PendingRiderRelease(riderId, orderId));
        } catch (DataIntegrityViolationException ignored) {
            // unique constraint on orderId — entry already queued, nothing to do
        }
    }
}
