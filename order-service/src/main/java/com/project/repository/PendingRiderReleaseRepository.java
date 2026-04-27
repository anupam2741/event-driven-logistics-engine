package com.project.repository;

import com.project.entity.PendingRiderRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PendingRiderReleaseRepository extends JpaRepository<PendingRiderRelease, UUID> {
    List<PendingRiderRelease> findAll();
}
