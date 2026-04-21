package com.project.kafka;

import com.project.dto.Coordinates;
import com.project.dto.OrderEvent;
import com.project.dto.OrderStatusUpdateEvent;
import com.project.service.RiderAssignmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderConsumerServiceTest {

    @Mock private RiderAssignmentService riderAssignmentService;
    @Mock private OrderStatusProducer orderStatusProducer;

    @InjectMocks
    private OrderConsumerService consumer;

    private OrderEvent buildEvent() {
        return new OrderEvent(
                "order-123", "CUST001",
                new Coordinates(12.97, 77.59),
                new Coordinates(12.98, 77.61),
                150.0, "MEDIUM", "RIDER_001", "ACCEPTED", LocalDateTime.now()
        );
    }

    @Test
    void handleOrderCreated_callsAssignRiders() {
        OrderEvent event = buildEvent();

        consumer.handleOrderCreated(event);

        verify(riderAssignmentService).assignRiders(event);
    }

    @Test
    void handleDlt_publishesCancelledStatusEvent() {
        OrderEvent event = buildEvent();
        ArgumentCaptor<OrderStatusUpdateEvent> captor =
                ArgumentCaptor.forClass(OrderStatusUpdateEvent.class);

        consumer.handleDlt(event, "order-topic-dlt");

        verify(orderStatusProducer).sendOrderStatusUpdateEvent(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("CANCELLED");
        assertThat(captor.getValue().orderId()).isEqualTo("order-123");
    }
}
