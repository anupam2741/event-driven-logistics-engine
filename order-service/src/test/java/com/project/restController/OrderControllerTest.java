package com.project.restController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.dto.Coordinates;
import com.project.dto.OrderRequestDto;
import com.project.dto.OrderResponseDto;
import com.project.exception.OrderCancellationException;
import com.project.exception.OrderNotFoundException;
import com.project.interfaces.OrderService;
import com.project.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "api.key=test-key",
        "demo.base-lat=12.9716",
        "demo.base-lng=77.5946",
        "demo.range=0.045"
})
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean OrderService orderService;

    private static final String API_KEY = "test-key";
    private static final String ORDER_ID = UUID.randomUUID().toString();

    private OrderRequestDto validRequest() {
        return new OrderRequestDto(
                "CUST001",
                new Coordinates(12.97, 77.59),
                new Coordinates(12.98, 77.61),
                BigDecimal.valueOf(150.0),
                "MEDIUM"
        );
    }

    private OrderResponseDto acceptedResponse() {
        return new OrderResponseDto(ORDER_ID, "ACCEPTED", "Order Placed");
    }

    @Test
    void createOrder_withApiKey_returns201() throws Exception {
        when(orderService.createOrder(any())).thenReturn(acceptedResponse());

        mockMvc.perform(post("/api/v1/order")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void createOrder_missingApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createOrder_invalidBody_returns400() throws Exception {
        String emptyBody = "{}";

        mockMvc.perform(post("/api/v1/order")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrderDetails_withApiKey_returns200() throws Exception {
        when(orderService.getOrderDetails(ORDER_ID)).thenReturn(acceptedResponse());

        mockMvc.perform(get("/api/v1/order/{id}", ORDER_ID)
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID));
    }

    @Test
    void cancelOrder_withApiKey_returns200() throws Exception {
        when(orderService.cancelOrder(ORDER_ID))
                .thenReturn(new OrderResponseDto(ORDER_ID, "CANCELLED", "Order cancelled successfully"));

        mockMvc.perform(patch("/api/v1/order/{id}/cancel", ORDER_ID)
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_orderNotFound_returns404() throws Exception {
        when(orderService.cancelOrder(ORDER_ID)).thenThrow(new OrderNotFoundException("Order not found"));

        mockMvc.perform(patch("/api/v1/order/{id}/cancel", ORDER_ID)
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelOrder_alreadyDelivered_returns409() throws Exception {
        when(orderService.cancelOrder(ORDER_ID))
                .thenThrow(new OrderCancellationException("Cannot cancel in DELIVERED state"));

        mockMvc.perform(patch("/api/v1/order/{id}/cancel", ORDER_ID)
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isConflict());
    }
}
