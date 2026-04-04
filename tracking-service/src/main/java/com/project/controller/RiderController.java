package com.project.controller;

import com.project.service.DataBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/riders")

public class RiderController {
    private final RedisTemplate<String,Object> redisTemplate;
    private final DataBootstrapService dataBootstrapService;

    public RiderController(@Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate, DataBootstrapService dataBootstrapService) {
        this.redisTemplate = redisTemplate;
        this.dataBootstrapService = dataBootstrapService;
    }

    @PostMapping("/location")
    public String updateLocation(@RequestParam String riderId ,@RequestParam double lat, @RequestParam double lng){
        redisTemplate.opsForGeo().add("available_riders",new Point(lng,lat),riderId);
        return "Rider " + riderId + "location Updated";
    }
    @PostMapping("/seedRiders")
    public String seedRiders(){
        dataBootstrapService.resetAndSeedRiders(10);
        return "10 riders seeded";
    }
}
