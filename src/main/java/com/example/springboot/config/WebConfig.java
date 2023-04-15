package com.example.springboot.config;

import com.example.springboot.web.interceptor.IfMatchInterceptor;
import com.example.springboot.web.interceptor.JwtTokenInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public JwtTokenInterceptor jwtTokenInterceptor() {
        return new JwtTokenInterceptor();
    }

    @Bean
    public IfMatchInterceptor ifMatchInterceptor() {
        return new IfMatchInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtTokenInterceptor() )
                .addPathPatterns("/plan/**"); // path that interceptor works

        registry.addInterceptor(ifMatchInterceptor())
                .addPathPatterns("/plan/put/**")
                .addPathPatterns("/plan/patch/**")
                .addPathPatterns("/plan/delete/**");
    }
}

