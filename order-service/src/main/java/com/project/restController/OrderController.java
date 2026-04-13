package com.project.restController;

import com.project.dto.Coordinates;
import com.project.dto.OrderRequestDto;
import com.project.dto.OrderResponseDto;
import com.project.dto.TrackingResponseDto;
import com.project.interfaces.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Random;

@RestController
@RequestMapping("/api/v1/order")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Place, retrieve, cancel and track orders")
public class OrderController {

    private final OrderService orderService;

    @Value("${demo.base-lat}") private double baseLat;
    @Value("${demo.base-lng}") private double baseLng;
    @Value("${demo.range}")    private double range;

    private final Random random = new Random();

    @Operation(summary = "Place a new order",
            description = "Checks rider availability via gRPC, creates the order, and publishes an event to assign a rider")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order accepted and rider assignment in progress"),
            @ApiResponse(responseCode = "200", description = "No riders available — order not placed"),
            @ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    @PostMapping
    public ResponseEntity<OrderResponseDto> createOrder(@Valid @RequestBody OrderRequestDto orderRequest) {
        log.info("order created");
        OrderResponseDto orderResponse = orderService.createOrder(orderRequest);
        return new ResponseEntity<>(orderResponse, HttpStatus.CREATED);
    }

    @Operation(summary = "Get order details", description = "Returns the current status and assigned rider for an order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponseDto> getOrderDetails(
            @Parameter(description = "UUID of the order") @PathVariable String orderId) {
        OrderResponseDto orderResponseDto = orderService.getOrderDetails(orderId);
        return new ResponseEntity<>(orderResponseDto, HttpStatus.OK);
    }

    @Operation(summary = "Cancel an order",
            description = "Cancels the order and releases the assigned rider back to the available pool via gRPC")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled successfully"),
            @ApiResponse(responseCode = "400", description = "Order is already delivered or cancelled"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponseDto> cancelOrder(
            @Parameter(description = "UUID of the order") @PathVariable String orderId) {
        OrderResponseDto response = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get live tracking",
            description = "Returns the rider's current GPS coordinates fetched from the tracking service")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tracking data returned"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @GetMapping("/{orderId}/tracking")
    public ResponseEntity<TrackingResponseDto> getOrderLiveTracking(
            @Parameter(description = "UUID of the order") @PathVariable String orderId) {
        TrackingResponseDto tracking = orderService.getLiveTracking(orderId);
        return ResponseEntity.ok(tracking);
    }

    @Operation(summary = "Place an order with random coordinates",
            description = "Generates random pickup and delivery coordinates within a 5 km radius of Bangalore city centre. " +
                    "Coordinates are guaranteed to fall within the same area as seeded riders. " +
                    "Seed riders first via /api/riders/seedRiders on the tracking service.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order placed, rider assignment in progress"),
            @ApiResponse(responseCode = "200", description = "No riders available in range")
    })
    @PostMapping("/create-demo-order")
    public ResponseEntity<OrderResponseDto> createOrderWithRandomCoordinates(
            @Parameter(description = "Order priority: HIGH, MEDIUM or LOW")
            @RequestParam(defaultValue = "MEDIUM") String priority) {

        Coordinates pickup   = randomCoords();
        Coordinates delivery = randomCoordsAwayFrom(pickup);

        OrderRequestDto request = new OrderRequestDto(
                "DEMO_CUSTOMER",
                pickup,
                delivery,
                BigDecimal.valueOf(150.00),
                priority
        );

        log.info("Random-coordinates order — pickup: {}, delivery: {}", pickup, delivery);
        OrderResponseDto response = orderService.createOrder(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    private Coordinates randomCoords() {
        double lat = baseLat + (random.nextDouble() * 2 - 1) * range;
        double lng = baseLng + (random.nextDouble() * 2 - 1) * range;
        return new Coordinates(
                Math.round(lat * 1_000_000d) / 1_000_000d,
                Math.round(lng * 1_000_000d) / 1_000_000d
        );
    }

    // Guarantees pickup-to-delivery distance >= 0.09 degrees.
    // At MOVEMENT_STEP=0.003 and PING_INTERVAL=2s: 0.09/0.003 = 30 steps x 2s = 60s minimum.
    private static final double MIN_DELIVERY_DISTANCE = 0.09;

    private Coordinates randomCoordsAwayFrom(Coordinates origin) {
        Coordinates candidate;
        do {
            candidate = randomCoords();
            double dist = Math.sqrt(
                    Math.pow(candidate.lat() - origin.lat(), 2) +
                    Math.pow(candidate.lng() - origin.lng(), 2)
            );
            if (dist >= MIN_DELIVERY_DISTANCE) return candidate;
        } while (true);
    }
}
