package com.project.service;

import com.project.grpc.AvailabilityRequest;
import com.project.grpc.AvailabilityResponse;
import com.project.grpc.RiderDiscoveryServiceGrpc;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public class OrderSafetyNetService {
    @GrpcClient("tracking-service")
    private RiderDiscoveryServiceGrpc.RiderDiscoveryServiceBlockingStub riderDiscoveryServiceBlockingStub;

    public AvailabilityResponse canPlaceOrder(double lat, double lng) {
        AvailabilityRequest request = AvailabilityRequest.newBuilder()
                .setLatitude(lat)
                .setLongitude(lng)
                .setRadiusKm(5.0)
                .build();

        try {
            return riderDiscoveryServiceBlockingStub.checkAvailability(request);
        } catch (Exception e) {
            return null;
        }
    }
}
