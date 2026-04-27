package com.project.kafka;

import com.project.entity.OrderStatus;
import com.project.dto.OrderStatusUpdateEvent;
import com.project.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class OrderUpdateConsumer {
    @Autowired
    private OrderRepository orderRepository;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = KafkaTopics.ORDER_STATUS_UPDATES, groupId = "order-service-group")
    public void handleStatusUpdate(OrderStatusUpdateEvent event) {
        log.info("Updating order {} to status {}", event.orderId(), event.status());
        String orderId = event.orderId();

        orderRepository.findById(UUID.fromString(orderId)).ifPresent(order -> {
            OrderStatus newStatus = OrderStatus.valueOf(event.status());
            if (order.getStatus().ordinal() >= newStatus.ordinal()) {
                log.warn("Skipping stale status update for order {} — current={}, incoming={}",
                        orderId, order.getStatus(), newStatus);
                return;
            }
            order.setStatus(newStatus);
            orderRepository.save(order);
        });
    }

    @DltHandler
    public void handleDlt(OrderStatusUpdateEvent event,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Status update exhausted retries and landed in DLT. topic={}, orderId={}",
                topic, event.orderId());
    }
}
