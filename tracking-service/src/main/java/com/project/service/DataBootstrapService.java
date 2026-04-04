package com.project.service;

import com.project.entity.RiderEntity;
import com.project.entity.RiderStatus;
import com.project.repository.RiderRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@Slf4j
public class DataBootstrapService {

    private final RiderRepository riderRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    public DataBootstrapService(RiderRepository riderRepository, @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.riderRepository = riderRepository;
        this.redisTemplate = redisTemplate;
    }

    private static final double BASE_LAT = 12.9716;
    private static final double BASE_LNG = 77.5946;

    @Transactional
    public void resetAndSeedRiders(int count) {
        log.info("Resetting system state for fresh testing...");
        riderRepository.deleteAllInBatch();

        redisTemplate.delete("available_riders");
        redisTemplate.delete("active_shipments");
        var lockKeys = redisTemplate.keys("lock:rider:*");
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
            log.info("Cleared {} existing soft locks from Redis.", lockKeys.size());
        }
        Random random = new Random();
        for (int i = 1; i <= count; i++) {
            String riderId = "RIDER_" + String.format("%03d", i);

            RiderEntity rider = new RiderEntity();
            rider.setId(riderId);
            rider.setName("Rider " + i);
            rider.setStatus(RiderStatus.AVAILABLE);
            riderRepository.save(rider);

            double randomLat = BASE_LAT + (random.nextDouble() - 0.5) / 10;
            double randomLng = BASE_LNG + (random.nextDouble() - 0.5) / 10;

            redisTemplate.opsForGeo().add(
                    "available_riders",
                    new Point(randomLng, randomLat),
                    riderId
            );
        }
        log.info("Successfully reset and seeded {} riders.", count);
    }
}