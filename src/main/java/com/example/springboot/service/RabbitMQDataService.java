package com.example.springboot.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.HashMap;
import java.util.Map;
//
//public class RabbitMQDataService{
//
//    @Autowired
//    private RedisTemplate<String, String> redisTemplate;
//
//    @Autowired
//    private RabbitTemplate rabbitTemplate;
//
//    //使用RedisTemplate将数据存储在Redis中，并使用RabbitTemplate将数据写入"myqueue"队列中。
//    public void saveData(String key, String value) {
//        redisTemplate.opsForValue().set(key, value);
//        Map<String, Object> data = new HashMap<>();
//        data.put("key", key);
//        data.put("value", value);
//        rabbitTemplate.convertAndSend("myqueue", data);
//    }
//}
