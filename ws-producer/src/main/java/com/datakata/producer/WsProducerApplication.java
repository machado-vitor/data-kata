package com.datakata.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WsProducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WsProducerApplication.class, args);
    }
}
