package com.project.kafka;

import com.project.dto.OrderStatusUpdateEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaProducerConfig {
    @Bean
    public KafkaTemplate<String, OrderStatusUpdateEvent> kafkaTemplate(ProducerFactory<String, OrderStatusUpdateEvent> pf) {
        return new KafkaTemplate<>(pf);
    }
}
