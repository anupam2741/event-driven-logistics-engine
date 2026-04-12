package com.project.kafka;

import com.project.dto.OrderEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderEventProducer {
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void sendOrderCreatedEvent(OrderEvent orderEvent){
        kafkaTemplate.send(KafkaTopics.ORDER_CREATED, orderEvent.orderId(), orderEvent);
    }
}
