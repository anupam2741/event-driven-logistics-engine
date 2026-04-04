package com.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.entity.RiderEntity;
import com.project.grpc.*;
import com.project.repository.RiderRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@GrpcService
@Slf4j
public class RiderDiscoveryGrpcServer extends RiderDiscoveryServiceGrpc.RiderDiscoveryServiceImplBase {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RiderRepository riderRepository;
    private final RiderAssignmentService riderAssignmentService;
    private final RiderNavigationService riderNavigationService;
    private final ObjectMapper objectMapper;

    public RiderDiscoveryGrpcServer(
            @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate,
            RiderRepository riderRepository,
            RiderAssignmentService riderAssignmentService,
            RiderNavigationService riderNavigationService,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.riderRepository = riderRepository;
        this.riderAssignmentService = riderAssignmentService;
        this.riderNavigationService = riderNavigationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void checkAvailability(AvailabilityRequest request, StreamObserver<AvailabilityResponse> responseObserver) {
        try{
        // Query Redis for riders within the requested radius
        GeoResults<RedisGeoCommands.GeoLocation<Object>> results = redisTemplate.opsForGeo()
                .search("available_riders",
                        GeoReference.fromCoordinate(request.getLongitude(), request.getLatitude()),
                        new Distance(request.getRadiusKm(), Metrics.KILOMETERS));
        if(results != null){
        for (GeoResult<RedisGeoCommands.GeoLocation<Object>> result : results) {
            String riderId = result.getContent().getName().toString();
            String lockKey = "lock:rider:" + riderId;

            // Try to acquire a 30-second "Soft Lock"
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "RESERVED", Duration.ofSeconds(30));

            if (Boolean.TRUE.equals(acquired)) {
                // Found a rider and reserved them!
                AvailabilityResponse availabilityResponse = AvailabilityResponse.newBuilder()
                        .setIsAvailable(true)
                        .setRiderId(riderId)
                        .build();
                responseObserver.onNext(availabilityResponse);
                responseObserver.onCompleted();
                return;
            }
        }
        }
         responseObserver.onNext(AvailabilityResponse.newBuilder().setIsAvailable(false).build());
        responseObserver.onCompleted();
}
        catch(Exception e){
            responseObserver.onError(Status.INTERNAL.asException());
        }
    }
    @Override
    public void getLiveLocation(LocationRequest request, StreamObserver<LocationResponse> responseObserver) {
        String riderId = request.getRiderId();

        try {
            // Fetch position from the 'active_shipments' Geo-index
            var positions = redisTemplate.opsForGeo().position("active_shipments", riderId);

            if (positions != null && !positions.isEmpty() && positions.getFirst() != null) {
                Point point = positions.getFirst();

                // Build success response
                LocationResponse response = LocationResponse.newBuilder()
                        .setLatitude(point.getY())
                        .setLongitude(point.getX())
                        .setIsActive(true)
                        .build();

                responseObserver.onNext(response);
            } else {
                // Fallback: Rider not found in active shipments (maybe offline or delivered)
                responseObserver.onNext(LocationResponse.newBuilder()
                        .setIsActive(false)
                        .build());
            }
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error fetching live location for rider: {}", riderId, e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to fetch coordinates")
                    .asRuntimeException());
        }
    }
    @Override
    public void releaseRider(ReleaseRiderRequest request, StreamObserver<ReleaseRiderResponse> responseObserver) {
        String riderId = request.getRiderId();
        String orderId = request.getOrderId();

        try {
            log.info("Received gRPC request to release rider {} from order {}", riderId, orderId);

            // Verify the rider is actually assigned to this order
            RiderEntity rider = riderRepository.findById(riderId).orElse(null);
            if (rider == null) {
                log.warn("Rider {} not found in database.", riderId);
                responseObserver.onNext(ReleaseRiderResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Rider not found.")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            if (rider.getCurrentOrderId() != null && !rider.getCurrentOrderId().equals(orderId)) {
                log.warn("Rider {} is assigned to order {}, not {}. Skipping release.",
                        riderId, rider.getCurrentOrderId(), orderId);
                responseObserver.onNext(ReleaseRiderResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Rider is assigned to a different order.")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            // Get last known position from active_shipments
            List<Point> positions = redisTemplate.opsForGeo().position("active_shipments", riderId);

            if (positions != null && !positions.isEmpty() && positions.getFirst() != null) {
                Point lastLocation = positions.getFirst();

                // Delegate Redis geo moves to RiderAssignmentService
                com.project.dto.Coordinates coords = new com.project.dto.Coordinates(
                        lastLocation.getY(), lastLocation.getX());
                riderAssignmentService.releaseRider(riderId, coords);

                // Update rider DB status
                riderAssignmentService.markRiderAsAvailable(riderId);

                // Publish cancellation to simulator so it stops moving
                publishCancellationToSimulator(riderId, orderId);

                log.info("Rider {} successfully released from order {} and moved to available pool at [{}, {}]",
                        riderId, orderId, lastLocation.getY(), lastLocation.getX());

                responseObserver.onNext(ReleaseRiderResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("Rider released successfully to available pool.")
                        .build());
            } else {
                // Rider not in active_shipments — still update DB status
                riderAssignmentService.markRiderAsAvailable(riderId);

                log.warn("Rider {} not found in active shipments. DB status updated, but no geo move performed.", riderId);

                responseObserver.onNext(ReleaseRiderResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("Rider DB status updated. No active geo position found.")
                        .build());
            }

            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error during gRPC ReleaseRider for rider {} order {}: {}", riderId, orderId, e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal server error during rider release")
                    .asException());
        }
    }

    private void publishCancellationToSimulator(String riderId, String orderId) {
        try {
            Map<String, Object> cancelMessage = new LinkedHashMap<>();
            cancelMessage.put("type", "CANCEL");
            cancelMessage.put("riderId", riderId);
            cancelMessage.put("orderId", orderId);

            String json = objectMapper.writeValueAsString(cancelMessage);
            riderNavigationService.publishRedis("rider_missions", json);
            log.info("Published cancellation message for rider {} order {}", riderId, orderId);
        } catch (Exception e) {
            log.error("Failed to publish cancellation message for rider {}: {}", riderId, e.getMessage());
        }
    }

}

