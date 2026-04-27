package com.project.service;

import com.project.grpc.AvailabilityRequest;
import com.project.grpc.AvailabilityResponse;
import com.project.grpc.RiderDiscoveryServiceGrpc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OrderSafetyNetService {

    @GrpcClient("tracking-service")
    private RiderDiscoveryServiceGrpc.RiderDiscoveryServiceBlockingStub riderDiscoveryServiceBlockingStub;

    @CircuitBreaker(name = "canPlaceOrder", fallbackMethod = "canPlaceOrderFallback")
    public AvailabilityResponse canPlaceOrder(double lat, double lng) {
        AvailabilityRequest request = AvailabilityRequest.newBuilder()
                .setLatitude(lat)
                .setLongitude(lng)
                .setRadiusKm(5.0)
                .build();
        return riderDiscoveryServiceBlockingStub.checkAvailability(request);
    }

    private AvailabilityResponse canPlaceOrderFallback(double lat, double lng, Throwable t) {
        log.warn("Circuit breaker open for canPlaceOrder — treating as no riders available. Cause: {}", t.getMessage());
        return AvailabilityResponse.newBuilder().setIsAvailable(false).build();
    }
}
