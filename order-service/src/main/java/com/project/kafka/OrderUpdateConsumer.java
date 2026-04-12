package com.project.kafka;

import com.project.entity.OrderStatus;
import com.project.dto.OrderStatusUpdateEvent;
import com.project.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class OrderUpdateConsumer {
    @Autowired
    private OrderRepository orderRepository;

    @KafkaListener(topics = KafkaTopics.ORDER_STATUS_UPDATES, groupId = "order-service-group")
    public void handleStatusUpdate(OrderStatusUpdateEvent event) {
        log.info("Updating order {} to status {}", event.orderId(), event.status());
        String cleanOrderId = event.orderId().replace("\"", "").trim();

        orderRepository.findById(UUID.fromString(cleanOrderId)).ifPresent(order -> {
            order.setStatus(OrderStatus.valueOf((event.status())));
            orderRepository.save(order);
        });
    }
}
