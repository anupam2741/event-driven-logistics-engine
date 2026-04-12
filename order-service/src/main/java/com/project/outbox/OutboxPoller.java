package com.project.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.dto.OrderEvent;
import com.project.entity.OutboxEvent;
import com.project.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    public void pollAndPublish() {
        List<OutboxEvent> pending = outboxRepository.findBySentFalse();
        for (OutboxEvent event : pending) {
            try {
                OrderEvent orderEvent = objectMapper.readValue(event.getPayload(), OrderEvent.class);
                kafkaTemplate.send(event.getTopic(), orderEvent.orderId(), orderEvent).get();
                event.setSent(true);
                outboxRepository.save(event);
                log.info("Outbox: published event for order {}", event.getOrderId());
            } catch (Exception e) {
                log.error("Outbox: failed to publish event for order {} — will retry", event.getOrderId(), e);
            }
        }
    }
}
