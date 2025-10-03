package com.nivleking.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
@EnableConfigurationProperties
public class UtilitiesApplication {

    public static void main(String[] args) {
        SpringApplication.run(UtilitiesApplication.class, args);
    }
}
