package com.project.outbox;

import com.project.entity.PendingRiderRelease;
import com.project.repository.PendingRiderReleaseRepository;
import com.project.service.OrderTrackingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PendingRiderReleasePoller {

    private final PendingRiderReleaseRepository repository;
    private final OrderTrackingClient trackingClient;

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void retryPendingReleases() {
        List<PendingRiderRelease> pending = repository.findAll();
        if (pending.isEmpty()) return;

        log.info("Retrying {} pending rider release(s)", pending.size());
        for (PendingRiderRelease entry : pending) {
            try {
                trackingClient.cancelOrder(entry.getRiderId(), entry.getOrderId());
                repository.delete(entry);
                log.info("Compensated rider release for order {}", entry.getOrderId());
            } catch (Exception e) {
                log.warn("Retry failed for order {} — will try again. Cause: {}", entry.getOrderId(), e.getMessage());
            }
        }
    }
}
