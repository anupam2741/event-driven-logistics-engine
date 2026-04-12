package com.project.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Data
@NoArgsConstructor
public class OutboxEvent {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private boolean sent = false;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public OutboxEvent(String orderId, String topic, String payload) {
        this.orderId = orderId;
        this.topic = topic;
        this.payload = payload;
    }
}
