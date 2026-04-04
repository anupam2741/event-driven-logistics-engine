package com.project.service;

import com.project.dto.Coordinates;
import com.project.dto.TrackingResponseDto;
import com.project.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OrderTrackingClient {
    @GrpcClient("tracking-service")
    private RiderDiscoveryServiceGrpc.RiderDiscoveryServiceBlockingStub trackingStub;
    public TrackingResponseDto fetchLiveLocation(String orderId, String riderId, String orderStatus) {
        try {
            LocationRequest request = LocationRequest.newBuilder()
                    .setRiderId(riderId)
                    .build();

            LocationResponse response = trackingStub.getLiveLocation(request);

            return new TrackingResponseDto(
                    orderId,
                    riderId,
                    new Coordinates(response.getLatitude(),response.getLongitude()),
                    orderStatus,
                    response.getIsActive()
            );
        } catch (Exception e) {
            log.error("gRPC Error: Could not fetch live tracking for rider {}", riderId, e);
            return new TrackingResponseDto(orderId, riderId, new Coordinates(0.0,0.0), orderStatus, false);
        }
    }
    public void cancelOrder(String riderId,String orderId) {
        try {
            ReleaseRiderRequest request = ReleaseRiderRequest.newBuilder()
                    .setRiderId(riderId)
                    .setOrderId(orderId)
                    .build();

            ReleaseRiderResponse response = trackingStub.releaseRider(request);

            if (response.getSuccess()) {
                log.info("Successfully released rider {} via gRPC", riderId);
            }
        } catch (Exception e) {
            log.error("Failed to release rider via gRPC: {}", e.getMessage());
        }
    }



}
