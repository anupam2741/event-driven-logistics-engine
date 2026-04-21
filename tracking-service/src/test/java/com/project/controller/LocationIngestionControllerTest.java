package com.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.dto.Coordinates;
import com.project.dto.OrderStatusUpdateEvent;
import com.project.dto.RiderLocationPing;
import com.project.kafka.OrderStatusProducer;
import com.project.security.SecurityConfig;
import com.project.service.RiderAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocationIngestionController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "api.key=test-key")
class LocationIngestionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean(name = "redisTemplate")
    RedisTemplate<String, Object> redisTemplate;

    @MockBean OrderStatusProducer orderStatusProducer;
    @MockBean RiderAssignmentService riderAssignmentService;

    @SuppressWarnings("unchecked")
    private GeoOperations<String, Object> geoOperations = mock(GeoOperations.class);

    private static final String API_KEY = "test-key";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
    }

    private String pingJson(String riderId, String orderId, String status, Double lat, Double lng) throws Exception {
        RiderLocationPing ping = new RiderLocationPing(riderId, orderId, status,
                lat != null && lng != null ? new Coordinates(lat, lng) : null);
        return objectMapper.writeValueAsString(ping);
    }

    @Test
    void ping_validPayload_returns202() throws Exception {
        mockMvc.perform(post("/api/tracking/ping")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pingJson("RIDER_001", "order-123", null, 12.97, 77.59)))
                .andExpect(status().isAccepted());

        verify(geoOperations).add(eq("active_shipments"), any(), eq("RIDER_001"));
    }

    @Test
    void ping_missingApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/tracking/ping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pingJson("RIDER_001", "order-123", null, 12.97, 77.59)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ping_blankRiderId_returns400() throws Exception {
        String body = "{\"riderId\":\"\",\"orderId\":\"order-123\",\"coordinates\":{\"lat\":12.97,\"lng\":77.59}}";

        mockMvc.perform(post("/api/tracking/ping")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ping_nullCoordinates_returns400() throws Exception {
        String body = "{\"riderId\":\"RIDER_001\",\"orderId\":\"order-123\",\"coordinates\":null}";

        mockMvc.perform(post("/api/tracking/ping")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ping_outOfRangeLat_returns400() throws Exception {
        String body = "{\"riderId\":\"RIDER_001\",\"orderId\":\"order-123\"," +
                "\"coordinates\":{\"lat\":200.0,\"lng\":77.59}}";

        mockMvc.perform(post("/api/tracking/ping")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ping_withNonNullStatus_publishesKafkaEvent() throws Exception {
        mockMvc.perform(post("/api/tracking/ping")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pingJson("RIDER_001", "order-123", "PICKED_UP", 12.97, 77.59)))
                .andExpect(status().isAccepted());

        ArgumentCaptor<OrderStatusUpdateEvent> captor = ArgumentCaptor.forClass(OrderStatusUpdateEvent.class);
        verify(orderStatusProducer).sendOrderStatusUpdateEvent(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("PICKED_UP");
    }

    @Test
    void ping_withDeliveredStatus_releasesRider() throws Exception {
        mockMvc.perform(post("/api/tracking/ping")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pingJson("RIDER_001", "order-123", "DELIVERED", 12.97, 77.59)))
                .andExpect(status().isAccepted());

        verify(riderAssignmentService).releaseRider(eq("RIDER_001"), any(Coordinates.class));
        verify(riderAssignmentService).markRiderAsAvailable("RIDER_001");
    }

    @Test
    void ping_withNullStatus_doesNotPublish() throws Exception {
        mockMvc.perform(post("/api/tracking/ping")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pingJson("RIDER_001", "order-123", null, 12.97, 77.59)))
                .andExpect(status().isAccepted());

        verify(orderStatusProducer, never()).sendOrderStatusUpdateEvent(any());
    }
}
