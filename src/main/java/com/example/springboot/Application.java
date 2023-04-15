package com.example.springboot;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@SpringBootApplication
@EnableRabbit
@EnableElasticsearchRepositories
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
	@Bean
	public ShallowEtagHeaderFilter filter(){
		return new ShallowEtagHeaderFilter();
	}

}
