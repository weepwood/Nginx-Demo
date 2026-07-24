package com.weepwood.nginxdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NginxDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(NginxDemoApplication.class, args);
    }
}
