package com.project.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pending_rider_releases")
@Data
@NoArgsConstructor
public class PendingRiderRelease {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false)
    private String riderId;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public PendingRiderRelease(String riderId, String orderId) {
        this.riderId = riderId;
        this.orderId = orderId;
    }
}
