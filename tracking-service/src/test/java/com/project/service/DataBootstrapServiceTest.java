package com.project.service;

import com.project.entity.RiderEntity;
import com.project.entity.RiderStatus;
import com.project.repository.RiderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataBootstrapServiceTest {

    @Mock private RiderRepository riderRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private GeoOperations<String, Object> geoOperations;

    private DataBootstrapService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        service = new DataBootstrapService(riderRepository, redisTemplate);
    }

    @Test
    void resetAndSeedRiders_clearsExistingRidersAndRedisKeys() {
        when(redisTemplate.keys("lock:rider:*")).thenReturn(Set.of());

        service.resetAndSeedRiders(3);

        verify(riderRepository).deleteAllInBatch();
        verify(redisTemplate).delete("available_riders");
        verify(redisTemplate).delete("active_shipments");
    }

    @Test
    void resetAndSeedRiders_seedsCorrectCount() {
        when(redisTemplate.keys("lock:rider:*")).thenReturn(Set.of());

        service.resetAndSeedRiders(5);

        ArgumentCaptor<RiderEntity> riderCaptor = ArgumentCaptor.forClass(RiderEntity.class);
        verify(riderRepository, times(5)).save(riderCaptor.capture());

        List<RiderEntity> saved = riderCaptor.getAllValues();
        assertThat(saved).hasSize(5);
        assertThat(saved).allMatch(r -> r.getStatus() == RiderStatus.AVAILABLE);
        verify(geoOperations, times(5)).add(eq("available_riders"), any(Point.class), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void resetAndSeedRiders_clearsLockKeys() {
        Set<String> lockKeys = Set.of("lock:rider:RIDER_001", "lock:rider:RIDER_002");
        when(redisTemplate.keys("lock:rider:*")).thenReturn((Set) lockKeys);

        service.resetAndSeedRiders(1);

        verify(redisTemplate).delete((Collection<String>) (Collection<?>) lockKeys);
    }
}
