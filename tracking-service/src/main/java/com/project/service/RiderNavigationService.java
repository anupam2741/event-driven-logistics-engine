package com.project.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RiderNavigationService {

    private final RedisTemplate<String, String> stringRedisTemplate;
    public RiderNavigationService(
            @Qualifier("messagingRedisTemplate") RedisTemplate<String, String> stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void publishRedis(String channel, String json){
        stringRedisTemplate.convertAndSend(channel, json);
    }
}
