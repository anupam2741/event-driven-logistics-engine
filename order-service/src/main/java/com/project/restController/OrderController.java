package com.project.restController;

import com.project.dto.OrderRequestDto;
import com.project.dto.OrderResponseDto;
import com.project.dto.TrackingResponseDto;
import com.project.interfaces.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/order")
@Slf4j
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponseDto> createOrder(@Valid @RequestBody OrderRequestDto orderRequest){
        log.info("order created");

        OrderResponseDto orderResponse = orderService.createOrder(orderRequest);
        return new ResponseEntity<>(orderResponse, HttpStatus.CREATED);
    }
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponseDto>getOrderDetails(@PathVariable String orderId){
        OrderResponseDto orderResponseDto = orderService.getOrderDetails(orderId);
        return new ResponseEntity<>(orderResponseDto,HttpStatus.OK);
    }
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponseDto> cancelOrder(@PathVariable String orderId){
        OrderResponseDto response = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/{orderId}/tracking")
    public ResponseEntity<TrackingResponseDto> getOrderLiveTracking(@PathVariable String orderId) {
        TrackingResponseDto tracking = orderService.getLiveTracking(orderId);
        return ResponseEntity.ok(tracking);
    }


}
