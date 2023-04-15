package com.example.springboot.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${spring.rabbitmq.host}")
    private String rabbitMQHost;

    @Value("${spring.rabbitmq.port}")
    private int rabbitMQPort;

    @Value("${spring.rabbitmq.username}")
    private String rabbitMQUsername;

    @Value("${spring.rabbitmq.password}")
    private String rabbitMQPassword;

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(rabbitMQHost, rabbitMQPort);
        connectionFactory.setUsername(rabbitMQUsername);
        connectionFactory.setPassword(rabbitMQPassword);
        return connectionFactory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate() {
        return new RabbitTemplate(connectionFactory());
    }

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange("plan-exchange");
    }

    @Bean
    public Queue queueCreate() {
        return new Queue("plan.create");
    }
    @Bean
    public Queue queuePatch() {
        return new Queue("plan.patch");
    }
//    @Bean
//    public Queue queuePut() {
//        return new Queue("plan.put");
//    }
    @Bean
    public Queue queueDelete() {
        return new Queue("plan.delete");
    }


    @Bean
    public Queue backupQueueCreate() {
        return new Queue("plan.create.backup");
    }
    @Bean
    public Queue backupQueuePatch() {
        return new Queue("plan.patch.backup");
    }
//    @Bean
//    public Queue backupPutDelete() {return new Queue("plan.put.backup");}
    @Bean
    public Queue backupQueueDelete() {return new Queue("plan.delete.backup");}


    @Bean
    //所有有"create"路由键的消息，会被绑定到这个queueCreate()生成的队列上
    public Binding bindingCreate() { return BindingBuilder.bind(queueCreate()).to(exchange()).with("create");}
    @Bean
    public Binding bindingPatch() {
        return BindingBuilder.bind(queuePatch()).to(exchange()).with("patch");
    }
//    @Bean
//    public Binding bindingPut() {
//        return BindingBuilder.bind(queuePut()).to(exchange()).with("put");
//    }
    @Bean
    public Binding bindingDelete() {
        return BindingBuilder.bind(queueDelete()).to(exchange()).with("delete");
    }



//    @Bean
//    public Binding binding() {
//        //所有以 "plan"开头的路由键，都会被绑定到这个queue()生成的队列上
//        return BindingBuilder.bind(queue()).to(exchange()).with("plan.#");
//    }
}
