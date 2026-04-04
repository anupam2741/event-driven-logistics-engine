package com.project.repository;

import com.project.entity.OrderEntity;
import com.project.interfaces.OrderTrackingInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    @Query("SELECT o.riderId as riderId, o.status as status FROM OrderEntity o WHERE o.id = :orderId")
    Optional<OrderTrackingInfo> findTrackingInfoById(UUID orderId);
}
