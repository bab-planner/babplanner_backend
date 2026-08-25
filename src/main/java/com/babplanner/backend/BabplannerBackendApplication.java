package com.babplanner.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BabplannerBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BabplannerBackendApplication.class, args);
    }
}
