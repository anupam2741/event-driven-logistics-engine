package com.project.repository;

import com.project.entity.RiderEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RiderRepository extends JpaRepository<RiderEntity, String> {

    @Modifying
    @Transactional
    @Query("UPDATE RiderEntity r SET r.status = 'BUSY', r.currentOrderId = :orderId " +
            "WHERE r.id = :riderId AND r.status = 'AVAILABLE'")
    int claimRiderIfAvailable(String riderId, String orderId);
}
