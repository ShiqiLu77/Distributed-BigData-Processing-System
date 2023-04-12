package com.example.springboot.dao.repository;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Repository
public class PlanRepository{

    @Autowired
    RedisTemplate<String, Object> redisTemplate;
    @Resource(name = "redisTemplate")
    ValueOperations<String, Object> valOps;

    public JsonNode getById(String id){
        JsonNode plan = (JsonNode) valOps.get(id);
        return plan;
    }

    public void save(String objectId, JsonNode plan){
        valOps.set(objectId, plan);
        //redisTemplate.opsForValue().set(objectId, plan);
    }

    public void delete(String id){
        boolean hasKey = redisTemplate.hasKey(id);
        if (hasKey) {
            redisTemplate.delete(id);
        }
    }

}
