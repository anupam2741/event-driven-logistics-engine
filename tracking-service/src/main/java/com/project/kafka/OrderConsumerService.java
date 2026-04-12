package com.project.kafka;

import com.project.dto.OrderEvent;
import com.project.service.RiderAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderConsumerService {
    private final RiderAssignmentService riderAssignmentService;
    @KafkaListener(topics = KafkaTopics.ORDER_CREATED, groupId = "tracking-group")
    public void handleOrderCreated(OrderEvent orderEvent) {
        riderAssignmentService.assignRiders(orderEvent);
        log.info("event :{}",orderEvent);
    }
}
