package com.project.controller;

import com.project.service.DataBootstrapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/riders")
@Tag(name = "Rider Management", description = "Manage rider positions and seed test data")
public class RiderController {
    private final RedisTemplate<String, Object> redisTemplate;
    private final DataBootstrapService dataBootstrapService;

    public RiderController(@Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate,
                           DataBootstrapService dataBootstrapService) {
        this.redisTemplate = redisTemplate;
        this.dataBootstrapService = dataBootstrapService;
    }

    @Operation(summary = "Manually update a rider's location",
            description = "Adds or updates a rider's GPS position in the available_riders Redis geo-index")
    @ApiResponse(responseCode = "200", description = "Location updated")
    @PostMapping("/location")
    public String updateLocation(
            @Parameter(description = "Rider ID") @RequestParam String riderId,
            @Parameter(description = "Latitude") @RequestParam double lat,
            @Parameter(description = "Longitude") @RequestParam double lng) {
        redisTemplate.opsForGeo().add("available_riders", new Point(lng, lat), riderId);
        return "Rider " + riderId + " location updated";
    }

    @Operation(summary = "Seed riders for testing",
            description = "Clears all existing riders and seeds 10 new riders with random positions around Bangalore. Use this to reset state before a demo.")
    @ApiResponse(responseCode = "200", description = "Riders seeded successfully")
    @PostMapping("/seedRiders")
    public String seedRiders() {
        dataBootstrapService.resetAndSeedRiders(10);
        return "10 riders seeded";
    }
}
