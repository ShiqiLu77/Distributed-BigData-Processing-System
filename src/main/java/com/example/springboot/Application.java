package com.example.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	public ShallowEtagHeaderFilter filter(){
		return new ShallowEtagHeaderFilter();
	}

//	@Bean
//	public FilterRegistrationBean<ShallowEtagHeaderFilter> shallowEtagHeaderFilter() {
//		FilterRegistrationBean<ShallowEtagHeaderFilter> filterRegistrationBean
//				= new FilterRegistrationBean<>( new ShallowEtagHeaderFilter());
//		filterRegistrationBean.addUrlPatterns("/foos/*");
//		filterRegistrationBean.setName("etagFilter");
//		return filterRegistrationBean;
//	}

//	@Bean
//	public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
//		return args -> {
//
//			System.out.println("Let's inspect the beans provided by Spring Boot:");
//
//			String[] beanNames = ctx.getBeanDefinitionNames();
//			Arrays.sort(beanNames);
//			for (String beanName : beanNames) {
//				System.out.println(beanName);
//			}
//		};
//	}
}
