package com.research.processing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.research.processing.LagPredictionService.REDIS_TTL_SECONDS;

@Service
public class RedisService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void storeData(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public Object getData(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void saveLagData(String consumerGroupId, long timestamp, String field, double value) {
        String key = String.format("lag:%s:%d", consumerGroupId, timestamp);
        redisTemplate.opsForHash().put(key, field, String.valueOf(value));
        redisTemplate.opsForHash().put(key, "timestamp", String.valueOf(timestamp));
        redisTemplate.expire(key, REDIS_TTL_SECONDS, TimeUnit.SECONDS);

        // Debug logging
        System.out.printf("Saved to Redis - Key: %s, Data: %s%n",
                key, redisTemplate.opsForHash().entries(key));
    }
}
