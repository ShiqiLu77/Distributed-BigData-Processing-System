package com.example.springboot.dao;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlanRepository{

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    public JsonNode getById(String id){
        JsonNode plan = (JsonNode) redisTemplate.opsForValue().get(id);
        return plan;
    }

    public void save(String objectId, JsonNode plan){
        redisTemplate.opsForValue().set(objectId, plan);
    }

    public void delete(String id){
        boolean hasKey = redisTemplate.hasKey(id);
        if (hasKey) {
            redisTemplate.delete(id);
        }
    }
}
