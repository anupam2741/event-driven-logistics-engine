package com.project.kafka;

import com.project.dto.OrderStatusUpdateEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderStatusProducer {
    private final KafkaTemplate<String, OrderStatusUpdateEvent> kafkaTemplate;
    public void sendOrderStatusUpdateEvent(OrderStatusUpdateEvent orderStatusUpdateEvent){
        kafkaTemplate.send("order-status-updates", orderStatusUpdateEvent.orderId(), orderStatusUpdateEvent);
    }

}
