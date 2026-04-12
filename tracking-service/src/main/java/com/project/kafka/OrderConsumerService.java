package com.project.kafka;

import com.project.dto.OrderEvent;
import com.project.service.RiderAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderConsumerService {
    private final RiderAssignmentService riderAssignmentService;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = KafkaTopics.ORDER_CREATED, groupId = "tracking-group")
    public void handleOrderCreated(OrderEvent orderEvent) {
        riderAssignmentService.assignRiders(orderEvent);
        log.info("Processed order event: {}", orderEvent.orderId());
    }

    @DltHandler
    public void handleDlt(OrderEvent orderEvent,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Order event exhausted retries and landed in DLT. topic={}, orderId={}",
                topic, orderEvent.orderId());
    }
}
