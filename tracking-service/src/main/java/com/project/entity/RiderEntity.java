package com.project.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "riders")
@Data
public class RiderEntity {
    @Id
    private String id;

    private String name;

    @Enumerated(EnumType.STRING)
    private RiderStatus status;

    private String currentOrderId;

    private LocalDateTime lastLocationUpdate;
}
