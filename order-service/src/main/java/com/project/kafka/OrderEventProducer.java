package com.project.kafka;

import com.project.dto.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderEventProducer {
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void sendOrderCreatedEvent(OrderEvent orderEvent) {
        kafkaTemplate.send(KafkaTopics.ORDER_CREATED, orderEvent.orderId(), orderEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCreated event for order {}", orderEvent.orderId(), ex);
                    } else {
                        log.debug("Published OrderCreated event for order {} to partition {}",
                                orderEvent.orderId(), result.getRecordMetadata().partition());
                    }
                });
    }
}
