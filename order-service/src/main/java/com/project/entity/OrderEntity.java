package com.project.entity;

import com.project.converter.CoordinatesConverter;
import com.project.dto.Coordinates;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEntity {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;
    @Column(nullable = false)
    private String customerId;
    @Convert(converter = CoordinatesConverter.class)
    @Column(nullable = false)
    private Coordinates pickupAddress;
    @Convert(converter = CoordinatesConverter.class)
    @Column(nullable = false)
    private Coordinates deliveryAddress;
    @Column(nullable = false)
    private Double totalAmount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderPriority priority;
    private String riderId;
    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
