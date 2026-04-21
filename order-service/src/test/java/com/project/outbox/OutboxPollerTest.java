package com.project.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.dto.Coordinates;
import com.project.dto.OrderEvent;
import com.project.entity.OutboxEvent;
import com.project.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxPoller poller;

    private OutboxEvent buildUnsentEvent() {
        OutboxEvent e = new OutboxEvent("order-123", "order-topic", "{\"orderId\":\"order-123\"}");
        return e;
    }

    private OrderEvent buildOrderEvent() {
        return new OrderEvent("order-123", "CUST001",
                new Coordinates(12.97, 77.59), new Coordinates(12.98, 77.61),
                150.0, "MEDIUM", "RIDER_001", "ACCEPTED", LocalDateTime.now());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pollAndPublish_pendingEvent_publishesAndMarksSent() throws Exception {
        OutboxEvent event = buildUnsentEvent();
        OrderEvent orderEvent = buildOrderEvent();

        when(outboxRepository.findBySentFalse()).thenReturn(List.of(event));
        when(objectMapper.readValue(event.getPayload(), OrderEvent.class)).thenReturn(orderEvent);

        CompletableFuture<SendResult<String, OrderEvent>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), any(OrderEvent.class))).thenReturn(future);

        poller.pollAndPublish();

        verify(kafkaTemplate).send("order-topic", "order-123", orderEvent);
        verify(outboxRepository).save(event);
        assert event.isSent();
    }

    @Test
    @SuppressWarnings("unchecked")
    void pollAndPublish_kafkaFails_eventRemainsUnsent() throws Exception {
        OutboxEvent event = buildUnsentEvent();
        OrderEvent orderEvent = buildOrderEvent();

        when(outboxRepository.findBySentFalse()).thenReturn(List.of(event));
        when(objectMapper.readValue(event.getPayload(), OrderEvent.class)).thenReturn(orderEvent);

        CompletableFuture<SendResult<String, OrderEvent>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(anyString(), anyString(), any(OrderEvent.class))).thenReturn(failedFuture);

        poller.pollAndPublish();

        assert !event.isSent();
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void pollAndPublish_noEvents_doesNotPublish() {
        when(outboxRepository.findBySentFalse()).thenReturn(List.of());

        poller.pollAndPublish();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }
}
