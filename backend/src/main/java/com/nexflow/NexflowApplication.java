package com.nexflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NexflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexflowApplication.class, args);
    }
}
